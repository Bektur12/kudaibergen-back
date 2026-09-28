package kg.kudaibergen.market;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import kg.kudaibergen.market.entity.Side;
import kg.kudaibergen.market.geo.GeoCalibration;
import kg.kudaibergen.market.geo.Point;
import kg.kudaibergen.market.geo.RowShape;
import kg.kudaibergen.market.route.RouteSteps;
import kg.kudaibergen.market.route.Router;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

   private final MapVersionRepository versions;
   private final MarketRowRepository rows;
   private final ContainerRepository containers;
   private final ObjectMapper mapper;
   private final ObjectProvider<ContainerTenants> tenants;
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

   public RowDetailDto row(Long rowId) {
      MarketSnapshot.RowView view = snapshot().row(rowId)
            .orElseThrow(() -> new NotFoundException("ROW_NOT_FOUND", "Ряд не найден"));
      Map<Long, ContainerTenants.Tenant> occupied = tenantsOf(view.all().stream()
            .map(c -> c.container().getId()).toList());
      List<RowDetailDto.SideDto> sides = new ArrayList<>();
      view.sides().forEach((side, list) -> sides.add(new RowDetailDto.SideDto(side, list.stream()
            .map(c -> {
               ContainerTenants.Tenant shop = occupied.get(c.container().getId());
               return new RowDetailDto.ContainerSlotDto(c.container().getId(), c.container().getNumber(),
                     shop != null, shop);
            })
            .toList())));
      return new RowDetailDto(view.row().getId(), view.row().getCode(), view.row().getLabel(), view.row().getType(),
            sides);
   }

   /** Контейнер по id — для модулей shops и requests. */
   public MarketSnapshot.ContainerView container(Long containerId) {
      return snapshot().container(containerId)
            .orElseThrow(() -> new NotFoundException("CONTAINER_NOT_FOUND", "Контейнер не найден"));
   }

   public LocationDto location(Long containerId) {
      return LocationDto.of(container(containerId));
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
         return new QrResolveDto(QrResolveDto.Type.CONTAINER, RowDto.of(view.row()), LocationDto.of(view),
               round(view.door().x()), round(view.door().y()));
      }
      MarketSnapshot.RowView row = snapshot.rowByToken(token)
            .orElseThrow(() -> new NotFoundException("QR_NOT_FOUND", "QR-код не относится к рынку"));
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
      MarketSnapshot.ContainerView target = container(containerId);

      Point start = from;
      RouteDto.FromSource source = RouteDto.FromSource.POINT;
      if (start == null && lat != null && lon != null) {
         Optional<Point> gps = snapshot.calibration().map(c -> c.toMap(lat, lon)).filter(snapshot::inside);
         if (gps.isPresent()) {
            start = gps.get();
            source = RouteDto.FromSource.GPS;
         }
      }
      if (start == null) {
         int index = entrance == null ? 0 : entrance;
         if (index < 0 || index >= snapshot.entrances().size()) {
            throw new NotFoundException("ENTRANCE_NOT_FOUND", "Нет такого входа");
         }
         start = snapshot.entrances().get(index).point();
         source = RouteDto.FromSource.ENTRANCE;
      }

      Router.Path path = snapshot.router().route(start, target.door());
      List<RouteSteps.Step> steps = RouteSteps.build(path, snapshot.passageNames(), target.center(),
            target.container().getNumber(), snapshot.metersPerPx(), lang);
      double meters = path.length() * snapshot.metersPerPx();
      int distance = (int) Math.round(meters);
      int minutes = (int) Math.max(1, Math.ceil(meters / WALK_METERS_PER_MINUTE));
      return new RouteDto(LocationDto.of(target), target.center().toArray(), start.toArray(), source,
            path.polyline().stream().map(Point::toArray).toList(), distance, minutes, steps);
   }

   /** Длина пешего пути по проходам от точки до контейнера в метрах — для «ближайший ряд, 170 м». */
   public double walkingMeters(Point from, Long containerId) {
      MarketSnapshot snapshot = snapshot();
      return snapshot.router().route(from, container(containerId).door()).length() * snapshot.metersPerPx();
   }

   // ─────────────────────── внутреннее ───────────────────────

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
      List<MapDto.MapRowDto> mapRows = snapshot.rows().stream().map(row -> {
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
