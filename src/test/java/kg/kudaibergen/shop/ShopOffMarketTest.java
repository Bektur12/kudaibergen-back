package kg.kudaibergen.shop;

import java.util.Set;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.shop.dto.ShopRequests;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Магазин вне рынка: адрес, точка и название обязательны, контейнера нет — ждёт подтверждения администрации. */
class ShopOffMarketTest {

   private final ShopService service = new ShopService(null, null, null, null, null, null, null, null, null, null,
         null, null, new ShopProperties(true));

   private static ShopRequests.CreateShop create(String address, Double lat, Double lng, String name) {
      return new ShopRequests.CreateShop(null, address, lat, lng, name, Set.of(1L), null, null, null, null, null);
   }

   private static ShopRequests.CreateShop manual(String place, String name) {
      return new ShopRequests.CreateShop(null, null, null, null, name, Set.of(1L), null, null, null, null, place);
   }

   @Test
   void внеРынкаЖдётПроверки() {
      Shop shop = service.newShop(7L, create("  пр. Чуй 155  ", 42.87, 74.6, "Kia центр"));
      assertThat(shop.isOnMarket()).isFalse();
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.PENDING_VERIFICATION);
      assertThat(shop.getAddress()).isEqualTo("пр. Чуй 155");
      assertThat(shop.getLat()).isEqualTo(42.87);
      assertThat(shop.containerToVerify()).isNull();
   }

   @Test
   void отклонённыйВнеРынкаПослеПравкиСноваНаПроверке() {
      Shop shop = service.newShop(7L, create("пр. Чуй 155", 42.87, 74.6, "Kia центр"));
      shop.reject("Нет такого адреса");
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.REJECTED);
      shop.resubmit();
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.PENDING_VERIFICATION);
      assertThat(shop.getBlockReason()).isNull();
      shop.verified();
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
   }

   @Test
   void безАдресаТочкиИлиНазвания() {
      assertThatThrownBy(() -> service.newShop(7L, create(" ", 42.87, 74.6, "Kia центр")))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("ADDRESS_REQUIRED");
      assertThatThrownBy(() -> service.newShop(7L, create("пр. Чуй 155", null, 74.6, "Kia центр")))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("POINT_REQUIRED");
      assertThatThrownBy(() -> service.newShop(7L, create("пр. Чуй 155", 42.87, 74.6, null)))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("NAME_REQUIRED");
   }

   @Test
   void местаНетВСписке_магазинНаРынкеБезКонтейнера() {
      Shop shop = service.newShop(7L, manual("  Ряд К,  конт. 5 ", null));
      assertThat(shop.isOnMarket()).isTrue();
      assertThat(shop.hasContainer()).isFalse();
      assertThat(shop.getContainerId()).isNull();
      assertThat(shop.getManualPlace()).isEqualTo("Ряд К, конт. 5");
      assertThat(shop.getAddress()).isEqualTo("Ряд К, конт. 5");
      assertThat(shop.getName()).isEqualTo("Ряд К, конт. 5");
      assertThat(shop.getLat()).isNull();
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.PENDING_VERIFICATION);
      assertThat(shop.containerToVerify()).isNull();
      shop.verified();
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);

      assertThat(service.newShop(7L, manual("Ряд К, конт. 5", "Автозапчасти Нурлан")).getName())
            .isEqualTo("Автозапчасти Нурлан");
   }

   @Test
   void местоТекстомИАдресВместе400() {
      var request = new ShopRequests.CreateShop(null, "пр. Чуй 155", 42.87, 74.6, null, Set.of(1L), null, null, null,
            null, "Ряд К, конт. 5");
      assertThatThrownBy(() -> service.newShop(7L, request))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("PLACE_CONFLICT");
      assertThatThrownBy(() -> service.newShop(7L, manual("К", null)))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("MANUAL_PLACE_LENGTH");
   }

   @Test
   void местоТекстомПереезжаетВКонтейнер() {
      Shop shop = service.newShop(7L, manual("Ряд К, конт. 5", null));
      shop.verified();
      shop.setPendingContainerId(42L);
      assertThat(shop.containerToVerify()).isEqualTo(42L);
      shop.verified();
      assertThat(shop.getContainerId()).isEqualTo(42L);
      assertThat(shop.getManualPlace()).isNull();
      assertThat(shop.getAddress()).isNull();
      assertThat(shop.isOnMarket()).isTrue();
   }

   @Test
   void рыночныйПроверяетКонтейнер() {
      Shop shop = new Shop(7L, "Автодеталь Азамат", 11L);
      assertThat(shop.isOnMarket()).isTrue();
      assertThat(shop.containerToVerify()).isEqualTo(11L);
      assertThatThrownBy(() -> shop.moveOffMarket("пр. Чуй 155", 42.87, 74.6)).isInstanceOf(IllegalStateException.class);
   }
}
