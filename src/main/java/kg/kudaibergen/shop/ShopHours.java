package kg.kudaibergen.shop;

import kg.kudaibergen.shop.dto.OpenStateDto;
import kg.kudaibergen.shop.entity.Shop;
import org.springframework.stereotype.Component;

/**
 * Открыт ли бокс: часов работы и тумблера «Бокс закрыт» больше нет — открыт каждый действующий магазин,
 * и только такие получают запросы. Часы в OpenStateDto — то, что сохранено у магазина, приложение их не показывает.
 */
@Component
public class ShopHours {

   public boolean openNow(Shop shop) {
      return shop.isActive();
   }

   public OpenStateDto state(Shop shop) {
      return new OpenStateDto(openNow(shop), false, shop.getOpenFrom(), shop.getOpenTo(), shop.workDaySet(), null);
   }
}
