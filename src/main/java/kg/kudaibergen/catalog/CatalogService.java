package kg.kudaibergen.catalog;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.catalog.dto.AppliedCarDto;
import kg.kudaibergen.catalog.dto.CarFilter;
import kg.kudaibergen.catalog.dto.PartCardDto;
import kg.kudaibergen.catalog.dto.PartDetailDto;
import kg.kudaibergen.catalog.dto.PartSearchResultDto;
import kg.kudaibergen.catalog.dto.PartSort;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.market.geo.Point;
import kg.kudaibergen.request.entity.PartCondition;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Каталог для покупателя (ТЗ 5): поиск и счётчик (27, 28), карточка (29), запчасти магазина (30),
 * избранное. Гостю доступно всё, кроме избранного и поиска по машине из гаража.
 */
@Service
public class CatalogService {

   /** Повторное открытие той же карточки тем же человеком в течение часа — не новый просмотр. */
   private static final Duration VIEW_DEDUP = Duration.ofHours(1);

   private final PartRepository parts;
   private final PartSearch search;
   private final CatalogView view;
   private final CarFilters carFilters;
   private final FavoritePartRepository favorites;
   private final ShopRepository shops;
   private final ShopAccess shopAccess;
   private final MarketMapService market;
   private final VehicleDirectory directory;
   private final StringRedisTemplate redis;

   public CatalogService(PartRepository parts, PartSearch search, CatalogView view, CarFilters carFilters,
                         FavoritePartRepository favorites, ShopRepository shops, ShopAccess shopAccess,
                         MarketMapService market, VehicleDirectory directory, StringRedisTemplate redis) {
      this.parts = parts;
      this.search = search;
      this.view = view;
      this.carFilters = carFilters;
      this.favorites = favorites;
      this.shops = shops;
      this.shopAccess = shopAccess;
      this.market = market;
      this.directory = directory;
      this.redis = redis;
   }

   /** Параметры строки поиска, фильтров и сортировки экрана 27. */
   public record SearchParams(String q, Long carId, Long brandId, Long modelId, Integer year, Long categoryId,
                              PartCondition condition, Integer priceMin,
                              Integer priceMax, Boolean inStock, Boolean openOnly, PartSort sort, Double x, Double y,
                              Long shopId) {
   }

   @Transactional(readOnly = true)
   public PartSearchResultDto search(Long viewerId, SearchParams params, String cursor, Integer limit) {
      CarFilter car = car(viewerId, params);
      PartSearch.Query query = query(params, car);
      int size = CursorPage.limit(limit);
      int offset = offset(cursor);
      List<PartSearch.Hit> hits = search.find(query, offset, size + 1);
      boolean more = hits.size() > size;
      List<Long> ids = (more ? hits.subList(0, size) : hits).stream().map(PartSearch.Hit::partId).toList();
      List<PartCardDto> cards = view.cards(ids, car, viewerId);
      return new PartSearchResultDto(cards, search.count(query), appliedCar(car),
            more ? CursorPage.encode(String.valueOf(offset + size)) : null);
   }

   /** «для Camry 50»: модель, а если выбрана только марка — марка. */
   private AppliedCarDto appliedCar(CarFilter car) {
      if (car == null) {
         return null;
      }
      Brand brand = directory.brand(car.brandId());
      String displayName = car.modelId() == null ? brand.getName() : directory.model(car.modelId()).label();
      return new AppliedCarDto(BrandDto.of(brand), car.modelId(), displayName, car.year(), car.label());
   }

   /** «Показать 86» в шторке моделей (28). */
   @Transactional(readOnly = true)
   public long count(Long viewerId, SearchParams params) {
      return search.count(query(params, car(viewerId, params)));
   }

   /**
    * Карточка (29). Покупатель видит только опубликованное у действующего магазина; люди бокса —
    * и свои черновики. Открытие покупателем — просмотр (не чаще раза в час на человека).
    */
   @Transactional
   public PartDetailDto detail(Long partId, Long viewerId, String viewerKey, Long carId, Long brandId, Long modelId,
                               Integer year, Lang lang) {
      Part part = parts.findById(partId).orElseThrow(CatalogService::notFound);
      Long viewerShopId = viewerId == null ? null
            : shopAccess.membership(viewerId).map(membership -> membership.shop().getId()).orElse(null);
      boolean own = Objects.equals(viewerShopId, part.getShopId());
      if (!own && !visible(part)) {
         throw notFound();
      }
      if (!own && Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("pv:" + partId + ":" + viewerKey, "1",
            VIEW_DEDUP))) {
         parts.countView(partId);
      }
      return view.detail(part, carFilters.resolve(viewerId, carId, brandId, modelId, year), viewerId, lang);
   }

   /** Ссылка «Поделиться» (29): карточка по публичному id, те же правила видимости. */
   @Transactional
   public PartDetailDto detailByPublicId(String publicId, Long viewerId, String viewerKey, Long carId, Long brandId,
                                         Long modelId, Integer year, Lang lang) {
      Long partId = parts.findIdByPublicId(publicId).orElseThrow(CatalogService::notFound);
      return detail(partId, viewerId, viewerKey, carId, brandId, modelId, year, lang);
   }

   // ─────────────────────── избранное ───────────────────────

   @Transactional
   public void addFavorite(Long userId, Long partId) {
      Part part = parts.findById(partId).filter(this::visible).orElseThrow(CatalogService::notFound);
      favorites.add(userId, part.getId());
   }

   @Transactional
   public void removeFavorite(Long userId, Long partId) {
      favorites.remove(userId, partId);
   }

   /** «Избранные запчасти» в профиле: снятые с продажи не показываем. */
   @Transactional(readOnly = true)
   public List<PartCardDto> favorites(Long userId) {
      List<Long> ids = favorites.favoritesOf(userId);
      Map<Long, Part> byId = parts.findAllById(ids).stream()
            .collect(Collectors.toMap(Part::getId, Function.identity()));
      List<Long> visible = ids.stream().filter(id -> byId.containsKey(id) && visible(byId.get(id))).toList();
      return view.cards(visible, null, userId);
   }

   // ─────────────────────── общее ───────────────────────

   private CarFilter car(Long viewerId, SearchParams params) {
      return carFilters.resolve(viewerId, params.carId(), params.brandId(), params.modelId(), params.year());
   }

   private PartSearch.Query query(SearchParams params, CarFilter car) {
      if (params.priceMin() != null && params.priceMax() != null && params.priceMin() > params.priceMax()) {
         throw new BadRequestException("BAD_PRICE_RANGE", "Цена «от» больше цены «до»");
      }
      PartSort sort = params.sort() == null ? PartSort.PRICE_ASC : params.sort();
      List<Long> shopOrder = sort == PartSort.NEAREST ? nearestShops(params.x(), params.y()) : List.of();
      return new PartSearch.Query(params.q(), car, params.categoryId(), params.condition(), params.priceMin(),
            params.priceMax(), params.inStock() == null || params.inStock(), Boolean.TRUE.equals(params.openOnly()),
            params.shopId(), sort, shopOrder);
   }

   /**
    * Действующие магазины от ближнего к дальнему — по прямой от точки покупателя на схеме,
    * без геолокации — от главного входа.
    */
   private List<Long> nearestShops(Double x, Double y) {
      MarketSnapshot snapshot = market.snapshot();
      Point from;
      if (x != null && y != null) {
         from = new Point(x, y);
      } else if (!snapshot.entrances().isEmpty()) {
         from = snapshot.entrances().get(0).point();
      } else {
         return List.of();
      }
      Point origin = from;
      return shops.findByStatus(ShopStatus.ACTIVE).stream()
            .map(shop -> Map.entry(shop.getId(), snapshot.container(shop.getContainerId())
                  .map(container -> distance(origin, container.center())).orElse(Double.MAX_VALUE)))
            .sorted(Map.Entry.comparingByValue(Comparator.naturalOrder()))
            .map(Map.Entry::getKey)
            .toList();
   }

   private static double distance(Point a, Point b) {
      return Math.hypot(a.x() - b.x(), a.y() - b.y());
   }

   /** Покупатель видит опубликованное у действующего магазина. */
   boolean visible(Part part) {
      return part.isActive() && shops.findById(part.getShopId()).map(Shop::isActive).orElse(false);
   }

   private static int offset(String cursor) {
      if (cursor == null || cursor.isBlank()) {
         return 0;
      }
      try {
         return Math.max(0, Integer.parseInt(CursorPage.decode(cursor)));
      } catch (NumberFormatException e) {
         throw new BadRequestException("BAD_CURSOR", "Некорректный курсор");
      }
   }

   static NotFoundException notFound() {
      return new NotFoundException("PART_NOT_FOUND", "Запчасть не найдена");
   }
}
