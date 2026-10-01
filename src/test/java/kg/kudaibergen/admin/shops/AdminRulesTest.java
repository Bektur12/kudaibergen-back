package kg.kudaibergen.admin.shops;

import kg.kudaibergen.admin.masters.MasterCheck;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRulesTest {

   @Test
   void сверкаСАрендатором() {
      assertThat(TenantMatch.of(true, true, true, true)).isEqualTo(TenantMatch.CONTAINER_TAKEN);
      assertThat(TenantMatch.of(false, true, false, false)).isEqualTo(TenantMatch.SMS_CONFIRMED);
      assertThat(TenantMatch.of(false, false, false, false)).isEqualTo(TenantMatch.AWAITING_CHECK);
      assertThat(TenantMatch.of(false, false, true, true)).isEqualTo(TenantMatch.IN_TENANT_LIST);
      assertThat(TenantMatch.of(false, false, true, false)).isEqualTo(TenantMatch.NOT_IN_LIST);
   }

   @Test
   void проверкаМастера() {
      assertThat(MasterCheck.of(3, 1)).isEqualTo(MasterCheck.COMPLAINT);
      assertThat(MasterCheck.of(0, 0)).isEqualTo(MasterCheck.NO_PHOTOS);
      assertThat(MasterCheck.of(2, 0)).isEqualTo(MasterCheck.PHOTOS);
   }

   @Test
   void отказНовомуМагазинуИВозвратНаПроверку() {
      Shop shop = new Shop(1L, "Бокс", 10L);

      shop.reject("Нет в списке");
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.REJECTED);
      assertThat(shop.getBlockReason()).isEqualTo("Нет в списке");
      assertThat(shop.containerToVerify()).isEqualTo(10L);

      shop.moveUnverified(11L);
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.PENDING_VERIFICATION);
      assertThat(shop.getBlockReason()).isNull();

      shop.reject("Снова нет");
      shop.verified();
      assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
   }

   @Test
   void отказВПереездеОставляетНаСтаромМесте() {
      Shop shop = new Shop(1L, "Бокс", 10L);
      shop.verified();
      shop.setPendingContainerId(20L);

      shop.reject("Не тот контейнер");

      assertThat(shop.getStatus()).isEqualTo(ShopStatus.ACTIVE);
      assertThat(shop.getPendingContainerId()).isNull();
      assertThat(shop.getContainerId()).isEqualTo(10L);
   }
}
