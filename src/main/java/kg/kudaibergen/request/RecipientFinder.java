package kg.kudaibergen.request;

import java.util.List;
import java.util.Objects;

import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.request.entity.RequestTarget;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopHours;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Кто получит запрос (ТЗ 4.3): магазин проверен, не закрыт вручную, сейчас его рабочее время,
 * марка машины есть в его марках, совпадает ряд или бокс, если выбран. Свой бокс покупателю не шлём.
 * Магазинов на рынке — до 2 000, поэтому часы и ряд проверяются в памяти.
 */
@Component
public class RecipientFinder {

   private final ShopRepository shops;
   private final ShopHours hours;
   private final ShopAccess access;
   private final MarketMapService market;

   public RecipientFinder(ShopRepository shops, ShopHours hours, ShopAccess access, MarketMapService market) {
      this.shops = shops;
      this.hours = hours;
      this.access = access;
      this.market = market;
   }

   @Transactional(readOnly = true)
   public List<Shop> find(Long buyerId, Long brandId, RequestTarget target, Long rowId, Long shopId) {
      Long ownShopId = buyerId == null ? null
            : access.membership(buyerId).map(membership -> membership.shop().getId()).orElse(null);
      return shops.findReceiving(ShopStatus.ACTIVE, brandId).stream()
            .filter(shop -> !Objects.equals(shop.getId(), ownShopId))
            .filter(shop -> switch (target) {
               case MARKET -> true;
               case ROW -> rowId.equals(market.container(shop.getContainerId()).row().row().getId());
               case SHOP -> shop.getId().equals(shopId);
            })
            .filter(hours::openNow)
            .toList();
   }
}
