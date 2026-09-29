package kg.kudaibergen.shop;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.category.Category;
import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.shop.dto.MapFilterKind;
import kg.kudaibergen.shop.dto.MapHighlightDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Подсветка рядов на карте по фильтру «Марка / Запчасть» (15): где торгуют маркой или категорией,
 * сколько боксов и рядов и какой бокс ближе всего пешком. Считаются только действующие магазины
 * в контейнерах текущей схемы; закрытые сейчас тоже подсвечиваются (их видно по openNowCount).
 */
@Service
public class MapHighlightService {

   private final ShopRepository shops;
   private final ShopHours hours;
   private final MarketMapService market;
   private final VehicleDirectory directory;
   private final CategoryService categories;

   public MapHighlightService(ShopRepository shops, ShopHours hours, MarketMapService market,
                              VehicleDirectory directory, CategoryService categories) {
      this.shops = shops;
      this.hours = hours;
      this.market = market;
      this.directory = directory;
      this.categories = categories;
   }

   @Transactional(readOnly = true)
   public MapHighlightDto highlight(MapFilterKind kind, Long id, MarketMapService.Start from, Lang lang) {
      String name;
      List<Shop> found;
      if (kind == MapFilterKind.BRAND) {
         name = directory.brand(id).getName();
         found = shops.findByStatusAndBrand(ShopStatus.ACTIVE, id);
      } else {
         Category category = Optional.ofNullable(categories.byId().get(id))
               .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Категория не найдена"));
         name = category.name(lang);
         found = shops.findByStatusAndCategory(ShopStatus.ACTIVE, id);
      }

      MarketSnapshot snapshot = market.snapshot();
      Map<Long, MarketSnapshot.ContainerView> placed = found.stream()
            .map(Shop::getContainerId).distinct()
            .map(snapshot::container).flatMap(Optional::stream)
            .collect(Collectors.toMap(view -> view.container().getId(), Function.identity()));
      List<Shop> onMap = found.stream().filter(shop -> placed.containsKey(shop.getContainerId())).toList();

      // ряды — в порядке сетки схемы, контейнеры — по id
      Set<Long> rowSet = placed.values().stream().map(view -> view.row().row().getId()).collect(Collectors.toSet());
      List<Long> rowIds = snapshot.rows().stream().map(row -> row.row().getId()).filter(rowSet::contains).toList();
      List<Long> containerIds = placed.keySet().stream().sorted().toList();
      int openNow = (int) onMap.stream().filter(hours::openNow).count();

      Optional<MarketMapService.Nearest> nearest = market.nearest(from.point(), containerIds);
      Long nearestShopId = nearest.flatMap(n -> onMap.stream()
            .filter(shop -> shop.getContainerId().equals(n.container().container().getId()))
            .map(Shop::getId).min(Comparator.naturalOrder())).orElse(null);
      return new MapHighlightDto(kind, id, name, rowIds, containerIds, onMap.size(), rowIds.size(), openNow,
            nearest.map(n -> n.container().row().row().getId()).orElse(null),
            nearest.map(n -> n.container().row().row().getCode()).orElse(null),
            nearest.map(n -> n.container().container().getId()).orElse(null),
            nearest.map(n -> (int) n.container().container().getNumber()).orElse(null),
            nearestShopId,
            nearest.map(MarketMapService.Nearest::meters).orElse(null),
            nearest.map(n -> MarketMapService.walkMinutes(n.meters())).orElse(null),
            from.source().name());
   }
}
