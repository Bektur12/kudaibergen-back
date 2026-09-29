package kg.kudaibergen.request;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.request.entity.RequestTarget;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopHours;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Кто получит запрос (спецификация 3.7а): магазин проверен, не закрыт вручную, сейчас его рабочее время.
 * «Всему рынку» и «Рядам» — только магазины с маркой машины (для рядов — ещё и в выбранных рядах);
 * «Контейнерам» — выбранные боксы без фильтра по марке: покупатель выбрал их сам.
 * Свой бокс покупателю не шлём. Магазинов на рынке — до 2 000, поэтому часы и ряд проверяются в памяти.
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
   public List<Shop> find(Long buyerId, Long brandId, RequestTarget target, Collection<Long> rowIds,
                          Collection<Long> containerIds) {
      Long ownShopId = buyerId == null ? null
            : access.membership(buyerId).map(membership -> membership.shop().getId()).orElse(null);
      List<Shop> candidates = target == RequestTarget.CONTAINERS
            ? shops.findReceivingIn(ShopStatus.ACTIVE, containerIds)
            : shops.findReceiving(ShopStatus.ACTIVE, brandId);
      Set<Long> rows = target == RequestTarget.ROWS ? Set.copyOf(rowIds) : Set.of();
      MarketSnapshot snapshot = market.snapshot();
      return candidates.stream()
            .filter(shop -> !Objects.equals(shop.getId(), ownShopId))
            .filter(shop -> target != RequestTarget.ROWS || snapshot.container(shop.getContainerId())
                  .map(container -> rows.contains(container.row().row().getId())).orElse(false))
            .filter(hours::openNow)
            .toList();
   }
}
