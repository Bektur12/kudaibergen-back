package kg.kudaibergen.catalog;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.catalog.comment.PartCommentRepository;
import kg.kudaibergen.catalog.dto.CarFilter;
import kg.kudaibergen.catalog.dto.PartCardDto;
import kg.kudaibergen.catalog.dto.PartSort;
import kg.kudaibergen.catalog.dto.VideoFeedItemDto;
import kg.kudaibergen.catalog.entity.Fitment;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.VideoDto;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.dto.ShopCardDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Лента видео «Рынок» и «Видео с рынка» на главной: опубликованные товары с видео у действующих боксов —
 * подходящие машине сверху (сначала именно к модели), дальше свежие.
 */
@Service
public class VideoFeedService {

   static final int DEFAULT_LIMIT = 10;
   /** «отвечает за 3 мин» — среднее время ответа на запросы за этот срок. */
   private static final Duration REPLY_PERIOD = Duration.ofDays(30);

   private final PartRepository parts;
   private final PartSearch search;
   private final CarFilters carFilters;
   private final MediaService media;
   private final ShopRepository shops;
   private final ShopMapper shopMapper;
   private final FavoritePartRepository favorites;
   private final VehicleDirectory directory;
   private final NamedParameterJdbcTemplate jdbc;
   private final Clock clock;
   private final PartCommentRepository comments;
   private final PartShareRepository shares;

   public VideoFeedService(PartRepository parts, PartSearch search, CarFilters carFilters, MediaService media,
                           ShopRepository shops, ShopMapper shopMapper, FavoritePartRepository favorites,
                           VehicleDirectory directory, NamedParameterJdbcTemplate jdbc, Clock clock,
                           PartCommentRepository comments, PartShareRepository shares) {
      this.parts = parts;
      this.search = search;
      this.carFilters = carFilters;
      this.media = media;
      this.shops = shops;
      this.shopMapper = shopMapper;
      this.favorites = favorites;
      this.directory = directory;
      this.jdbc = jdbc;
      this.clock = clock;
      this.comments = comments;
      this.shares = shares;
   }

   @Transactional(readOnly = true)
   public VideoFeedItemDto.VideoFeedPageDto feed(Long viewerId, Long carId, Long brandId, Long modelId, Integer year,
                                     String cursor, Integer limit) {
      CarFilter car = carFilters.resolve(viewerId, carId, brandId, modelId, year);
      int size = limit == null ? DEFAULT_LIMIT : CursorPage.limit(limit);
      int offset = offset(cursor);
      PartSearch.Query query = new PartSearch.Query(null, car, null, null, null, null, true, false, null,
            PartSort.NEWEST, List.of(), true);
      List<PartSearch.Hit> hits = search.find(query, offset, size + 1);
      boolean more = hits.size() > size;
      List<Long> ids = (more ? hits.subList(0, size) : hits).stream().map(PartSearch.Hit::partId).toList();
      return new VideoFeedItemDto.VideoFeedPageDto(items(ids, car, viewerId),
            more ? CursorPage.encode(String.valueOf(offset + size)) : null);
   }

   private List<VideoFeedItemDto> items(List<Long> ids, CarFilter car, Long viewerId) {
      Map<Long, Part> byId = parts.findAllById(ids).stream()
            .collect(Collectors.toMap(Part::getId, Function.identity()));
      List<Part> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
      Map<Long, VideoDto> videos = media.videos(ordered.stream().map(Part::getVideoMediaId).toList());
      List<Long> shopIds = ordered.stream().map(Part::getShopId).distinct().toList();
      Map<Long, ShopCardDto> cards = new HashMap<>();
      shops.findAllById(shopIds).forEach(shop -> cards.put(shop.getId(), shopMapper.card(shop)));
      Map<Long, Integer> replyMinutes = replyMinutes(shopIds);
      Set<Long> favorite = favorites.favoriteAmong(viewerId, ids);
      Map<Long, Integer> favoriteCounts = favorites.counts(ids);
      Map<Long, Integer> commentCounts = comments.counts(ids);
      Map<Long, Integer> shareCounts = shares.counts(ids);
      List<VideoFeedItemDto> items = new ArrayList<>();
      for (Part part : ordered) {
         VideoDto video = videos.get(part.getVideoMediaId());
         ShopCardDto shop = cards.get(part.getShopId());
         if (video == null || shop == null) {
            continue;
         }
         items.add(new VideoFeedItemDto(part.getId(), part.getPublicId(), part.getTitle(), part.getPrice(),
               PartCardDto.KGS, part.getCondition(), video, car == null ? null : Fits.anyFits(part.getFitments(), car),
               fitsLabel(part.getFitments()), part.isShipsToRegions(), favorite.contains(part.getId()),
               favoriteCounts.getOrDefault(part.getId(), 0), commentCounts.getOrDefault(part.getId(), 0),
               shareCounts.getOrDefault(part.getId(), 0),
               new VideoFeedItemDto.VideoShopDto(shop.id(), shop.name(), shop.avatarUrl(),
                     ShopMapper.place(shop), shop.phone(), replyMinutes.get(shop.id()))));
      }
      return items;
   }

   /** «Поделиться» в ленте: +1 к счётчику; гостю тоже. */
   @Transactional
   public void share(Long partId) {
      if (!shares.increment(partId)) {
         throw CatalogService.notFound();
      }
   }

   /**
    * «Подходят на Camry 50 и 55 · 2011–2017»: модели одной линейки — через «и» одним названием, годы — от самого
    * раннего до самого позднего (из машин, иначе из годов моделей). Марка без модели — «Toyota».
    */
   String fitsLabel(List<Fitment> fitments) {
      if (fitments.isEmpty()) {
         return null;
      }
      Map<String, List<String>> generations = new LinkedHashMap<>();
      Integer from = null;
      Integer to = null;
      boolean openEnd = false;
      for (Fitment fitment : fitments) {
         Integer yearFrom = fitment.getYearFrom();
         Integer yearTo = fitment.getYearTo();
         if (fitment.getModelId() == null) {
            generations.computeIfAbsent(directory.brand(fitment.getBrandId()).getName(), key -> new ArrayList<>());
         } else {
            CarModel model = directory.model(fitment.getModelId());
            boolean custom = model.getDisplayName() != null && !model.getDisplayName().isBlank();
            if (custom || model.getGeneration() == null) {
               generations.computeIfAbsent(model.label(), key -> new ArrayList<>());
            } else {
               List<String> list = generations.computeIfAbsent(model.getName(), key -> new ArrayList<>());
               if (!list.contains(model.getGeneration())) {
                  list.add(model.getGeneration());
               }
            }
            if (yearFrom == null && model.getYearFrom() != null) {
               yearFrom = (int) model.getYearFrom();
            }
            if (yearTo == null && model.getYearTo() != null) {
               yearTo = (int) model.getYearTo();
            }
         }
         if (yearFrom != null) {
            from = from == null ? yearFrom : Math.min(from, yearFrom);
         }
         if (yearTo == null) {
            openEnd = openEnd || yearFrom != null;
         } else {
            to = to == null ? yearTo : Math.max(to, yearTo);
         }
      }
      List<String> names = new ArrayList<>();
      generations.forEach((name, list) -> names.add(list.isEmpty() ? name : name + " " + and(list)));
      String years = from == null ? "" : openEnd || to == null ? " · с " + from : " · " + from + "–" + to;
      return "Подходят на " + and(names) + years;
   }

   /** «a», «a и b», «a, b и c». */
   static String and(List<String> items) {
      if (items.size() <= 1) {
         return items.isEmpty() ? "" : items.get(0);
      }
      return String.join(", ", items.subList(0, items.size() - 1)) + " и " + items.get(items.size() - 1);
   }

   /** Среднее время ответа на запросы за 30 дней, минут (не меньше 1). */
   private Map<Long, Integer> replyMinutes(List<Long> shopIds) {
      Map<Long, Integer> result = new HashMap<>();
      if (shopIds.isEmpty()) {
         return result;
      }
      jdbc.query("""
            select rr.shop_id, avg(extract(epoch from rr.replied_at - rr.notified_at))::float8 as avg_sec
            from request_recipients rr
            where rr.shop_id in (:shopIds) and rr.notified_at >= :since and rr.replied_at is not null
            group by rr.shop_id""",
            new MapSqlParameterSource("shopIds", shopIds)
                  .addValue("since", Timestamp.from(clock.instant().minus(REPLY_PERIOD))),
            rs -> {
               result.put(rs.getLong("shop_id"), (int) Math.max(1, Math.round(rs.getDouble("avg_sec") / 60)));
            });
      return result;
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
}
