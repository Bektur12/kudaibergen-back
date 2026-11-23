package kg.kudaibergen.request;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.request.entity.RequestTarget;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Кто получит запрос (спецификация 3.7а): магазин проверен и не на паузе за «сказали «есть», а не было» (15).
 * Часы работы не проверяются: запрос приходит и вечером, и в выходной — продавец ответит, когда увидит
 * (ночью пуш без звука, см. QuietHours).
 * «Всему рынку» и «Рядам» — только магазины с маркой машины (для рядов — ещё и в выбранных рядах);
 * «Контейнерам» — выбранные боксы без фильтра по марке: покупатель выбрал их сам.
 * Свой бокс покупателю не шлём. Магазинов на рынке — до 2 000, поэтому ряд проверяется в памяти.
 */
@Component
public class RecipientFinder {

   private final ShopRepository shops;
   private final ShopAccess access;
   private final MarketMapService market;
   private final Clock clock;

   public RecipientFinder(ShopRepository shops, ShopAccess access, MarketMapService market,
                          Clock clock) {
      this.shops = shops;
      this.access = access;
      this.market = market;
      this.clock = clock;
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
      Instant now = clock.instant();
      return candidates.stream()
            .filter(shop -> !shop.requestsPausedAt(now))
            .filter(shop -> !Objects.equals(shop.getId(), ownShopId))
            .filter(shop -> target != RequestTarget.ROWS || snapshot.container(shop.getContainerId())
                  .map(container -> rows.contains(container.row().row().getId())).orElse(false))
            .toList();
   }
}
