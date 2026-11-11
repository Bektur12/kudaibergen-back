package kg.kudaibergen.market;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.market.dto.LocateDto;
import kg.kudaibergen.market.dto.LocateRequest;
import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.market.dto.MapDto;
import kg.kudaibergen.market.dto.MarketSearchDto;
import kg.kudaibergen.market.dto.QrResolveDto;
import kg.kudaibergen.market.dto.RouteDto;
import kg.kudaibergen.market.dto.RowDetailDto;
import kg.kudaibergen.market.dto.RowDto;
import kg.kudaibergen.market.entity.Container;
import kg.kudaibergen.market.entity.MarketRow;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import kg.kudaibergen.market.geo.GeoCalibration;
import kg.kudaibergen.market.geo.Point;
import kg.kudaibergen.market.geo.RowShape;
import kg.kudaibergen.market.route.RouteSteps;
import kg.kudaibergen.market.route.Router;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Карта рынка: схема, ряды и контейнеры, привязка GPS, QR, поиск и маршрут.
 * Схема держится в памяти снимком ({@link MarketSnapshot}); снимок перечитывается после правок
 * в админке и раз в минуту — чтобы правки с другого инстанса тоже доехали.
 */
@Service
public class MarketMapService {

   static final Duration SNAPSHOT_TTL = Duration.ofMinutes(1);
   /** Пешком по рынку, м/мин: с толкучкой медленнее, чем по улице. */
   static final double WALK_METERS_PER_MINUTE = 60;
   static final int NEAREST_CANDIDATES = 8;

   private final MapVersionRepository versions;
   private final MarketRowRepository rows;
   private final ContainerRepository containers;
   private final ObjectMapper mapper;
   private final ObjectProvider<ContainerTenants> tenants;
   private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MarketMapService.class);

   private final AtomicReference<Cached> cache = new AtomicReference<>();

   public MarketMapService(MapVersionRepository versions, MarketRowRepository rows, ContainerRepository containers,
                           ObjectMapper mapper, ObjectProvider<ContainerTenants> tenants) {
      this.versions = versions;
      this.rows = rows;
      this.containers = containers;
      this.mapper = mapper;
      this.tenants = tenants;
   }

   // ─────────────────────── снимок ───────────────────────

   public MarketSnapshot snapshot() {
      return cached().snapshot;
   }

   private Cached cached() {
      Cached cached = cache.get();
      if (cached == null || cached.loadedAt.plus(SNAPSHOT_TTL).isBefore(Instant.now())) {
         cached = load();
         cache.set(cached);
      }
      return cached;
   }

   /** Перечитать схему (после правок в админке). */
   public void reload() {
      cache.set(null);
   }

   private Cached load() {
      var version = versions.findByCurrentTrue()
            .orElseThrow(() -> new IllegalStateException("Нет текущей версии схемы рынка"));
      MarketSnapshot snapshot = new MarketSnapshot(version, rows.findByActiveTrueOrderBySortOrder(),
            containers.findByActiveTrue(), mapper);
      return new Cached(snapshot, buildMap(snapshot), Instant.now());
   }

   // ─────────────────────── карта и ряды ───────────────────────

   public MapDto map() {
      return cached().map;
   }

   public int currentVersion() {
      return snapshot().version();
   }

   public List<RowDto> rows() {
      return snapshot().rows().stream().map(RowDto::of).toList();
   }

   /** brandId — для экрана 31: какие продавцы ряда торгуют маркой машины. */
   public RowDetailDto row(Long rowId, Long brandId) {
      MarketSnapshot.RowView view = snapshot().row(rowId)
            .orElseThrow(() -> new NotFoundException("ROW_NOT_FOUND", "Ряд не найден"));
      Map<Long, ContainerTenants.Tenant> occupied = tenantsOf(view.all().stream()
            .map(c -> c.container().getId()).toList());
      List<RowDetailDto.SideDto> sides = new ArrayList<>();
      int[] brandSellers = {0};
      view.sides().forEach((side, list) -> sides.add(new RowDetailDto.SideDto(side, list.stream()
            .map(c -> {
               ContainerTenants.Tenant tenant = occupied.get(c.container().getId());
               // занято, но магазин покупателю не показывается (на проверке, переезжает) — shopId = null
               ContainerTenants.Tenant shop = tenant == null || tenant.shopId() == null ? null : tenant;
               Boolean sellsBrand = brandId == null ? null : shop != null && shop.brandIds().contains(brandId);
               if (Boolean.TRUE.equals(sellsBrand)) {
                  brandSellers[0]++;
               }
               return new RowDetailDto.ContainerSlotDto(c.container().getId(), c.container().getNumber(),
                     c.container().getSide(), tenant != null, shop == null ? RowDetailDto.ContainerState.NO_SELLER
                     : RowDetailDto.ContainerState.HAS_SELLER, sellsBrand, shop);
            })
            .toList())));
      return new RowDetailDto(view.row().getId(), view.row().getCode(), view.row().getLabel(), view.row().getType(),
            sides, brandId == null ? null : brandSellers[0]);
   }

   // ─────────────────────── продавец добавляет ряд ───────────────────────

   /** Ряд из ответа POST /rows: created = false — такой ряд уже был (или включён снова). */
   public record RowResult(RowDto row, boolean created) {
   }

   private static final Map<Character, Character> LATIN_LOOKALIKES = Map.ofEntries(
         Map.entry('A', 'А'), Map.entry('B', 'В'), Map.entry('C', 'С'), Map.entry('E', 'Е'), Map.entry('H', 'Н'),
         Map.entry('K', 'К'), Map.entry('M', 'М'), Map.entry('O', 'О'), Map.entry('P', 'Р'), Map.entry('T', 'Т'),
         Map.entry('X', 'Х'), Map.entry('Y', 'У'));
   private static final java.util.regex.Pattern ROW_CODE =
         java.util.regex.Pattern.compile("[\\p{IsCyrillic}\\p{IsLatin}0-9 -]{1,10}");

   /**
    * Код ряда, как его ввёл продавец → код в базе: без пробелов по краям и слова «ряд», заглавными,
    * латинские двойники — кириллицей («k» → «К»). Буквы, цифры, пробел и дефис, 1–10 символов, иначе
    * 400 BAD_ROW_CODE.
    */
   static String normalizeRowCode(String raw) {
      String code = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
      code = code.replaceFirst("(?iu)^ряд(\\s+|$)", "").strip().toUpperCase(Locale.ROOT);
      StringBuilder mapped = new StringBuilder(code.length());
      for (char ch : code.toCharArray()) {
         mapped.append(LATIN_LOOKALIKES.getOrDefault(ch, ch));
      }
      code = mapped.toString();
      if (!ROW_CODE.matcher(code).matches() || code.chars().noneMatch(Character::isLetterOrDigit)) {
         throw new BadRequestException("BAD_ROW_CODE",
               "Номер ряда — до 10 символов: буквы, цифры, пробел или дефис");
      }
      return code;
   }

   /**
    * Продавец не нашёл ряд в списке и добавляет его: ряд без формы на схеме (onMap = false), контейнеры —
    * через POST /rows/{id}/containers. Такой ряд уже есть — он и возвращается (выключенный включается).
    */
   @Transactional
   public RowResult addRow(String rawCode, Long userId) {
      String code = normalizeRowCode(rawCode);
      MarketRow row = rows.findByCodeIgnoreCase(code).stream().findFirst().orElse(null);
      boolean created = false;
      if (row == null) {
         Short max = rows.maxSortOrder();
         row = rows.save(new MarketRow(code, "Ряд " + code, RowType.ROW, null, (short) ((max == null ? 0 : max) + 1),
               UUID.randomUUID().toString().replace("-", "")));
         created = true;
         log.info("Пользователь {} добавил ряд «{}» (id {})", userId, code, row.getId());
      } else if (!row.isActive()) {
         row.setActive(true);
         log.info("Пользователь {} снова включил ряд «{}» (id {})", userId, row.getCode(), row.getId());
      }
      // и найденный ряд мог быть в снимке выключенным — перечитываем всегда, это дёшево
      reloadAfterCommit();
      int count = (int) containers.findByRowId(row.getId()).stream().filter(Container::isActive).count();
      return new RowResult(new RowDto(row.getId(), row.getCode(), row.getLabel(), row.getType(), count,
            row.hasGeometry()), created);
   }

   // ─────────────────────── продавец добавляет контейнер ───────────────────────

   /**
    * Продавец не нашёл свой контейнер в сетке ряда и добавляет его (номер 1..999). Сторона — см.
    * {@link #resolveSide}. Такой же контейнер уже есть — 409 CONTAINER_EXISTS; был, но выключен — включается.
    * Снимок перечитывается сразу, контейнер появляется в сетке и на карте.
    */
   @Transactional
   public RowDetailDto.ContainerSlotDto addContainer(Long rowId, int number, Side requested) {
      MarketRow row = rows.findById(rowId).filter(MarketRow::isActive)
            .orElseThrow(() -> new NotFoundException("ROW_NOT_FOUND", "Ряд не найден"));
      List<Container> existing = containers.findByRowId(rowId);
      Set<Side> used = existing.stream().filter(Container::isActive).map(Container::getSide)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Side.class)));
      Side side = resolveSide(row, used, requested);
      Container container = existing.stream()
            .filter(c -> c.getSide() == side && c.getNumber() == number)
            .findFirst().orElse(null);
      if (container != null) {
         if (container.isActive()) {
            throw containerExists(row, number);
         }
         container.setActive(true);
      } else {
         container = new Container(rowId, side, (short) number, UUID.randomUUID().toString().replace("-", ""));
         try {
            containers.saveAndFlush(container);
         } catch (DataIntegrityViolationException race) {
            throw containerExists(row, number);
         }
      }
      reloadAfterCommit();
      ContainerTenants.Tenant tenant = tenantsOf(List.of(container.getId())).get(container.getId());
      ContainerTenants.Tenant shop = tenant == null || tenant.shopId() == null ? null : tenant;
      return new RowDetailDto.ContainerSlotDto(container.getId(), container.getNumber(), container.getSide(),
            tenant != null, shop == null ? RowDetailDto.ContainerState.NO_SELLER
            : RowDetailDto.ContainerState.HAS_SELLER, null, shop);
   }

   /**
    * Сторона нового контейнера: переданная (должна быть у ряда, иначе 400 WRONG_SIDE); без неё — единственная
    * занятая сторона ряда; занято две — 400 SIDE_REQUIRED; контейнеров нет — NORTH (вертикальный ряд — EAST).
    */
   static Side resolveSide(MarketRow row, Set<Side> used, Side requested) {
      List<Side> allowed = List.of(Side.of(row.getType()));
      if (requested != null) {
         if (!allowed.contains(requested)) {
            throw new BadRequestException("WRONG_SIDE", "У ряда " + row.getLabel() + " стороны " + allowed);
         }
         return requested;
      }
      if (used.size() == 1) {
         return used.iterator().next();
      }
      if (used.size() > 1) {
         throw new BadRequestException("SIDE_REQUIRED", "У ряда " + row.getLabel()
               + " контейнеры с двух сторон — выберите сторону");
      }
      return row.getType() == RowType.VROW ? Side.EAST : Side.NORTH;
   }

   private static ConflictException containerExists(MarketRow row, int number) {
      return new ConflictException("CONTAINER_EXISTS", "В ряду " + row.getLabel() + " на этой стороне уже есть "
            + "контейнер " + number);
   }

   /** Перечитать снимок сейчас и ещё раз после коммита — чтобы параллельный запрос не закэшировал старую схему. */
   private void reloadAfterCommit() {
      reload();
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
         TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
               reload();
            }
         });
      }
   }

   /** Контейнер по id — для модулей shops и requests. */
   public MarketSnapshot.ContainerView container(Long containerId) {
      return snapshot().container(containerId)
            .orElseThrow(() -> new NotFoundException("CONTAINER_NOT_FOUND", "Контейнер не найден"));
   }

   /** Место контейнера; null — контейнера нет (магазин вне рынка). */
   public LocationDto location(Long containerId) {
      return containerId == null ? null : LocationDto.of(container(containerId));
   }

   // ─────────────────────── где я ───────────────────────

   /** GPS → точка схемы. Без калибровки — 409, клиент показывает карту без точки. */
   public LocateDto locate(LocateRequest request) {
      MarketSnapshot snapshot = snapshot();
      GeoCalibration calibration = snapshot.calibration().orElseThrow(() ->
            new ConflictException("MAP_NOT_CALIBRATED", "Карта ещё не привязана к GPS — выберите ряд вручную"));
      Point point = calibration.toMap(request.lat(), request.lon());
      double accuracy = request.accuracyM() == null ? 0 : request.accuracyM();
      return new LocateDto(round(point.x()), round(point.y()), round(accuracy / snapshot.metersPerPx()),
            snapshot.inside(point));
   }

   /** Внутри ли рынка GPS-точка; пусто — карта не откалибрована и проверить нельзя. */
   public Optional<Boolean> insideMarket(double lat, double lon) {
      MarketSnapshot snapshot = snapshot();
      return snapshot.calibration().map(calibration -> snapshot.inside(calibration.toMap(lat, lon)));
   }

   public QrResolveDto resolveQr(String token) {
      MarketSnapshot snapshot = snapshot();
      Optional<MarketSnapshot.ContainerView> container = snapshot.containerByToken(token);
      if (container.isPresent()) {
         MarketSnapshot.ContainerView view = container.get();
         Point door = view.door();
         return new QrResolveDto(QrResolveDto.Type.CONTAINER, RowDto.of(view.row()), LocationDto.of(view),
               door == null ? null : round(door.x()), door == null ? null : round(door.y()));
      }
      MarketSnapshot.RowView row = snapshot.rowByToken(token)
            .orElseThrow(() -> new NotFoundException("QR_NOT_FOUND", "QR-код не относится к рынку"));
      if (!row.onMap()) {
         // ряда нет на схеме — точку «я здесь» не ставим
         return new QrResolveDto(QrResolveDto.Type.ROW, RowDto.of(row), null, null, null);
      }
      // табличка висит в начале ряда
      Point start = row.shape().axis().get(0);
      return new QrResolveDto(QrResolveDto.Type.ROW, RowDto.of(row), null, round(start.x()), round(start.y()));
   }

   // ─────────────────────── поиск ───────────────────────

   /** «14», «ряд ю», «14 12», «жайма 3 5»: ряды и контейнеры. */
   public MarketSearchDto search(String query) {
      String normalized = normalize(query).replaceAll("\\b(ряд|ряда|катар|бокс|контейнер|конт)\\b", " ")
            .trim().replaceAll(" +", " ");
      if (normalized.isEmpty()) {
         return new MarketSearchDto(List.of(), List.of());
      }
      List<RowDto> foundRows = new ArrayList<>();
      List<LocationDto> foundContainers = new ArrayList<>();
      for (MarketSnapshot.RowView row : snapshot().rows()) {
         String code = normalize(row.row().getCode());
         if (normalized.equals(code) || (normalized.length() < code.length() && code.startsWith(normalized))) {
            foundRows.add(RowDto.of(row));
         } else if (normalized.startsWith(code + " ")) {
            String rest = normalized.substring(code.length() + 1);
            if (rest.matches("\\d+")) {
               int number = Integer.parseInt(rest);
               row.all().stream()
                     .filter(c -> c.container().getNumber() == number)
                     .map(LocationDto::of)
                     .forEach(foundContainers::add);
            }
         }
      }
      return new MarketSearchDto(foundRows, foundContainers);
   }

   // ─────────────────────── маршрут ───────────────────────

   /**
    * Маршрут до контейнера. Откуда: точка схемы (from), иначе GPS (если карта откалибрована
    * и точка на рынке), иначе вход fromEntrance (по умолчанию — главный).
    */
   public RouteDto route(Long containerId, Point from, Double lat, Double lon, Integer entrance, Lang lang) {
      MarketSnapshot snapshot = snapshot();
      MarketSnapshot.ContainerView target = requireOnMap(container(containerId));
      Start origin = start(from, lat, lon, entrance);
      Point start = origin.point();
      RouteDto.FromSource source = origin.source();

      Router.Path path = snapshot.router().route(start, target.door());
      List<RouteSteps.Step> steps = RouteSteps.build(path, snapshot.passageNames(), target.center(),
            target.container().getNumber(), snapshot.metersPerPx(), lang);
      double meters = path.length() * snapshot.metersPerPx();
      int distance = (int) Math.round(meters);
      int minutes = walkMinutes(meters);
      return new RouteDto(LocationDto.of(target), target.center().toArray(), start.toArray(), source,
            path.polyline().stream().map(Point::toArray).toList(), distance, minutes, steps);
   }

   /** Откуда идёт человек и как это определили. */
   public record Start(Point point, RouteDto.FromSource source) {
   }

   /**
    * Точка старта: точка схемы (from), иначе GPS (если карта откалибрована и точка на рынке),
    * иначе вход entrance (по умолчанию — главный).
    */
   public Start start(Point from, Double lat, Double lon, Integer entrance) {
      if (from != null) {
         return new Start(from, RouteDto.FromSource.POINT);
      }
      MarketSnapshot snapshot = snapshot();
      if (lat != null && lon != null) {
         Optional<Point> gps = snapshot.calibration().map(c -> c.toMap(lat, lon)).filter(snapshot::inside);
         if (gps.isPresent()) {
            return new Start(gps.get(), RouteDto.FromSource.GPS);
         }
      }
      int index = entrance == null ? 0 : entrance;
      if (index < 0 || index >= snapshot.entrances().size()) {
         throw new NotFoundException("ENTRANCE_NOT_FOUND", "Нет такого входа");
      }
      return new Start(snapshot.entrances().get(index).point(), RouteDto.FromSource.ENTRANCE);
   }

   /** Ближайший пешком контейнер и путь до него в метрах. */
   public record Nearest(MarketSnapshot.ContainerView container, int meters) {
   }

   /**
    * Ближайший по проходам контейнер из списка (подсветка на карте, 15): «ближайший ряд 14, 170 м».
    * Пешком меряются только {@link #NEAREST_CANDIDATES} ближайших по прямой — путь по проходам
    * почти не отличается от прямой настолько, чтобы дальний по прямой оказался ближе всех.
    * Контейнеры не со схемы пропускаются.
    */
   public Optional<Nearest> nearest(Point from, Collection<Long> containerIds) {
      MarketSnapshot snapshot = snapshot();
      return containerIds.stream().distinct()
            .map(snapshot::container).flatMap(Optional::stream)
            .filter(MarketSnapshot.ContainerView::onMap)
            .sorted(Comparator.comparingDouble(view -> straight(from, view.door())))
            .limit(NEAREST_CANDIDATES)
            .map(view -> new Nearest(view, (int) Math.round(
                  snapshot.router().route(from, view.door()).length() * snapshot.metersPerPx())))
            .min(Comparator.comparingInt(Nearest::meters));
   }

   /** Минуты пешком, не меньше одной (60 м/мин). */
   public static int walkMinutes(double meters) {
      return (int) Math.max(1, Math.ceil(meters / WALK_METERS_PER_MINUTE));
   }

   private static double straight(Point a, Point b) {
      return Math.hypot(a.x() - b.x(), a.y() - b.y());
   }

   /** Длина пешего пути по проходам от точки до контейнера в метрах — для «ближайший ряд, 170 м». */
   public double walkingMeters(Point from, Long containerId) {
      MarketSnapshot snapshot = snapshot();
      return snapshot.router().route(from, requireOnMap(container(containerId)).door()).length()
            * snapshot.metersPerPx();
   }

   /** Минуты пешком от главного входа: «Ряд 14 · конт. 12 · 2 мин» в ответах (05); пусто — места нет на схеме. */
   public Optional<Integer> walkMinutesFromEntrance(Long containerId) {
      MarketSnapshot snapshot = snapshot();
      if (containerId == null || snapshot.entrances().isEmpty()) {
         return Optional.empty();
      }
      Point entrance = snapshot.entrances().get(0).point();
      return snapshot.container(containerId).filter(MarketSnapshot.ContainerView::onMap).map(view ->
            walkMinutes(snapshot.router().route(entrance, view.door()).length() * snapshot.metersPerPx()));
   }

   // ─────────────────────── внутреннее ───────────────────────

   /** Маршрут и расстояние — только до контейнера, который нарисован на схеме. */
   private static MarketSnapshot.ContainerView requireOnMap(MarketSnapshot.ContainerView view) {
      if (!view.onMap()) {
         throw new ConflictException("CONTAINER_NOT_ON_MAP", view.row().row().getLabel()
               + " ещё не нарисован на схеме рынка — маршрут построить нельзя");
      }
      return view;
   }

   private Map<Long, ContainerTenants.Tenant> tenantsOf(Collection<Long> containerIds) {
      ContainerTenants provider = tenants.getIfAvailable();
      return provider == null || containerIds.isEmpty() ? Map.of() : provider.byContainers(containerIds);
   }

   private MapDto buildMap(MarketSnapshot snapshot) {
      JsonNode json = snapshot.json();
      List<MapDto.PassageDto> passages = new ArrayList<>();
      json.get("passages").forEach(passage -> passages.add(new MapDto.PassageDto(
            RowShape.points(passage.get("points")).stream().map(Point::toArray).toList(),
            passage.get("width").asDouble())));
      // ряды без формы на схеме не рисуются: они есть только в списке рядов (GET /rows)
      List<MapDto.MapRowDto> mapRows = snapshot.rows().stream().filter(MarketSnapshot.RowView::onMap).map(row -> {
         JsonNode geometry;
         try {
            geometry = mapper.readTree(row.row().getGeometry());
         } catch (Exception e) {
            throw new IllegalStateException(e);
         }
         List<MapDto.MapContainerDto> cells = row.all().stream()
               .map(c -> new MapDto.MapContainerDto(c.container().getId(), c.container().getNumber(),
                     c.container().getSide(), round(c.center().x()), round(c.center().y()), c.angle(),
                     round(c.cellLength())))
               .toList();
         return new MapDto.MapRowDto(row.row().getId(), row.row().getCode(), row.row().getLabel(),
               row.row().getType(), geometry, row.shape().center().toArray(),
               row.shape().axis().stream().map(Point::toArray).toList(), row.shape().halfWidth(), cells);
      }).toList();
      return new MapDto(snapshot.version(), "пиксели схемы рынка, ось Y вниз", json.get("boundary"),
            json.get("blocks"), passages, json.get("entrances"), json.get("pois"), json.get("streets"),
            json.get("labels"), mapRows, Math.round(snapshot.metersPerPx() * 10_000) / 10_000.0,
            snapshot.calibration().orElse(null));
   }

   static String normalize(String text) {
      return text == null ? "" : text.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim();
   }

   private static double round(double value) {
      return Math.round(value * 10) / 10.0;
   }

   private record Cached(MarketSnapshot snapshot, MapDto map, Instant loadedAt) {
   }

   /** Стороны ряда — для админки и валидации. */
   static Side[] sidesOf(MarketSnapshot.RowView row) {
      return Side.of(row.row().getType());
   }
}
