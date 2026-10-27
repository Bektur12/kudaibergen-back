package kg.kudaibergen.shop;

import java.util.Set;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.shop.dto.ShopRequests;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Магазин вне рынка: адрес, точка и название обязательны, проверки места нет — действует сразу. */
class ShopOffMarketTest {

   private final ShopService service = new ShopService(null, null, null, null, null, null, null, null, null, null,
         null, null, new ShopProperties(true));

   private static ShopRequests.CreateShop create(String address, Double lat, Double lng, String name) {
      return new ShopRequests.CreateShop(null, address, lat, lng, name, Set.of(1L), null, null, null, null);
   }

   @Test
   void внеРынкаДействуетСразуДажеСПроверкой() {
      Shop shop = service.newShop(7L, create("  пр. Чуй 155  ", 42.87, 74.6, "Kia центр"));
      assertThat(shop.isOnMarket()).isFalse();
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
      assertThat(shop.getAddress()).isEqualTo("пр. Чуй 155");
      assertThat(shop.getLat()).isEqualTo(42.87);
      assertThat(shop.containerToVerify()).isNull();
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
   void рыночныйПроверяетКонтейнер() {
      Shop shop = new Shop(7L, "Автодеталь Азамат", 11L);
      assertThat(shop.isOnMarket()).isTrue();
      assertThat(shop.containerToVerify()).isEqualTo(11L);
      assertThatThrownBy(() -> shop.moveOffMarket("пр. Чуй 155", 42.87, 74.6)).isInstanceOf(IllegalStateException.class);
   }
}
