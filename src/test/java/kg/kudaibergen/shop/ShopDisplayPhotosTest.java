package kg.kudaibergen.shop;

import java.util.List;

import kg.kudaibergen.shop.entity.Shop;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Покупателю картинка продавца — баннер (первое фото места); нет баннера — старая аватарка, и она же вместо баннера. */
class ShopDisplayPhotosTest {

   private static Shop shop(Long avatar, List<Long> photos) {
      Shop shop = new Shop(1L, "Ряд 3, конт. 7", 10L);
      shop.setAvatarMediaId(avatar);
      shop.replacePhotos(photos);
      return shop;
   }

   @Test
   void подставляютсяДругДругу() {
      assertThat(shop(null, List.of(5L, 6L)).displayAvatarId()).isEqualTo(5L);
      assertThat(shop(9L, List.of()).displayPhotoIds()).containsExactly(9L);
   }

   @Test
   void естьОба_баннерВместоАватарки() {
      assertThat(shop(9L, List.of(5L)).displayAvatarId()).isEqualTo(5L);
      assertThat(shop(9L, List.of(5L)).displayPhotoIds()).containsExactly(5L);
      assertThat(shop(null, List.of()).displayAvatarId()).isNull();
      assertThat(shop(null, List.of()).displayPhotoIds()).isEmpty();
   }
}
