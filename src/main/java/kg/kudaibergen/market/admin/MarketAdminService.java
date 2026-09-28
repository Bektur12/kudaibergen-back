package kg.kudaibergen.market.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.market.ContainerRepository;
import kg.kudaibergen.market.ContainerTenants;
import kg.kudaibergen.market.GeoAnchorRepository;
import kg.kudaibergen.market.MapVersionRepository;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.MarketRowRepository;
import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.market.admin.AdminMarketDtos.AdminContainerDto;
import kg.kudaibergen.market.admin.AdminMarketDtos.CalibrationDto;
import kg.kudaibergen.market.admin.AdminMarketDtos.GeoAnchorsRequest;
import kg.kudaibergen.market.admin.AdminMarketDtos.MapUploadRequest;
import kg.kudaibergen.market.admin.AdminMarketDtos.PublishedMapDto;
import kg.kudaibergen.market.admin.AdminMarketDtos.RowUpload;
import kg.kudaibergen.market.admin.AdminMarketDtos.UpdateContainerRequest;
import kg.kudaibergen.market.entity.Container;
import kg.kudaibergen.market.entity.GeoAnchor;
import kg.kudaibergen.market.entity.MapVersion;
import kg.kudaibergen.market.entity.MarketRow;
import kg.kudaibergen.market.entity.Side;
import kg.kudaibergen.market.geo.GeoCalibration;
import kg.kudaibergen.market.geo.RowShape;
import kg.kudaibergen.market.route.PassageGraph;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Админка карты (ТЗ, раздел 2): админ рынка ведёт контейнеры, арендаторов и QR-наклейки;
 * суперадмин — схему карты и опорные GPS-точки.
 */
@Service
public class MarketAdminService {

   private static final int QR_SIZE = 360;

   private final MarketMapService market;
   private final MarketRowRepository rows;
   private final ContainerRepository containers;
   private final MapVersionRepository versions;
   private final GeoAnchorRepository anchors;
   private final ObjectProvider<ContainerTenants> tenants;
   private final ObjectMapper mapper;
   private final String qrBaseUrl;

   public MarketAdminService(MarketMapService market, MarketRowRepository rows, ContainerRepository containers,
                             MapVersionRepository versions, GeoAnchorRepository anchors,
                             ObjectProvider<ContainerTenants> tenants, ObjectMapper mapper, AppProperties properties) {
      this.market = market;
      this.rows = rows;
      this.containers = containers;
      this.versions = versions;
      this.anchors = anchors;
      this.tenants = tenants;
      this.mapper = mapper;
      this.qrBaseUrl = properties.market().qrBaseUrl();
   }

   // ─────────────────────── контейнеры ───────────────────────

   /**
    * Задать число мест на сторонах ряда. Недостающие номера создаются, лишние выключаются;
    * выключить место, где стоит магазин, нельзя.
    */
   @Transactional
   public List<AdminContainerDto> setContainerCounts(Long rowId, Map<Side, Integer> counts) {
      MarketRow row = rows.findById(rowId).orElseThrow(rowNotFound());
      Set<Side> allowed = Set.of(Side.of(row.getType()));
      if (!allowed.containsAll(counts.keySet())) {
         throw new BadRequestException("WRONG_SIDE", "У ряда " + row.getLabel() + " стороны " + allowed);
      }
      List<Container> existing = containers.findByRowId(rowId);
      List<Long> toDisable = new ArrayList<>();
      for (Map.Entry<Side, Integer> entry : counts.entrySet()) {
         Side side = entry.getKey();
         int count = entry.getValue();
         Map<Integer, Container> byNumber = existing.stream()
               .filter(c -> c.getSide() == side)
               .collect(Collectors.toMap(c -> (int) c.getNumber(), Function.identity()));
         for (int number = 1; number <= count; number++) {
            Container container = byNumber.get(number);
            if (container == null) {
               containers.save(new Container(rowId, side, (short) number, newToken()));
            } else {
               container.setActive(true);
            }
         }
         byNumber.values().stream()
               .filter(c -> c.getNumber() > count && c.isActive())
               .forEach(c -> toDisable.add(c.getId()));
      }
      if (!toDisable.isEmpty()) {
         ContainerTenants provider = tenants.getIfAvailable();
         Map<Long, ContainerTenants.Tenant> occupied = provider == null ? Map.of() : provider.byContainers(toDisable);
         if (!occupied.isEmpty()) {
            throw new ConflictException("CONTAINER_OCCUPIED",
                  "В убираемых местах стоят магазины: сначала переселите их");
         }
         containers.findAllById(toDisable).forEach(c -> c.setActive(false));
      }
      market.reload();
      return containers.findByRowId(rowId).stream()
            .sorted((a, b) -> a.getSide() == b.getSide() ? Short.compare(a.getNumber(), b.getNumber())
                  : a.getSide().compareTo(b.getSide()))
            .map(c -> toDto(c, row))
            .toList();
   }

   @Transactional
   public AdminContainerDto updateContainer(Long containerId, UpdateContainerRequest request) {
      Container container = containers.findById(containerId)
            .orElseThrow(() -> new NotFoundException("CONTAINER_NOT_FOUND", "Контейнер не найден"));
      if (request.tenantPhone() != null) {
         container.setTenantPhone(request.tenantPhone().isBlank() ? null : request.tenantPhone());
      }
      if (request.active() != null && request.active() != container.isActive()) {
         if (!request.active()) {
            ContainerTenants provider = tenants.getIfAvailable();
            if (provider != null && !provider.byContainers(List.of(containerId)).isEmpty()) {
               throw new ConflictException("CONTAINER_OCCUPIED", "В контейнере стоит магазин");
            }
         }
         container.setActive(request.active());
      }
      market.reload();
      return toDto(container, rows.findById(container.getRowId()).orElseThrow());
   }

   // ─────────────────────── GPS ───────────────────────

   /** Заменить опорные точки и пересчитать матрицу GPS → схема в текущей версии карты. */
   @Transactional
   public CalibrationDto setGeoAnchors(GeoAnchorsRequest request) {
      List<GeoCalibration.Anchor> points = request.anchors().stream()
            .map(a -> new GeoCalibration.Anchor(a.lat(), a.lon(), a.x(), a.y()))
            .toList();
      GeoCalibration calibration;
      try {
         calibration = GeoCalibration.fit(points);
      } catch (IllegalArgumentException e) {
         throw new BadRequestException("BAD_ANCHORS", e.getMessage());
      }
      anchors.deleteAllInBatch();
      request.anchors().forEach(a -> anchors.save(new GeoAnchor(BigDecimal.valueOf(a.lat()),
            BigDecimal.valueOf(a.lon()), BigDecimal.valueOf(a.x()), BigDecimal.valueOf(a.y()), a.label())));
      MapVersion current = currentVersion();
      current.setGeoAffine(write(calibration));
      market.reload();

      List<CalibrationDto.AnchorResidual> residuals = new ArrayList<>();
      for (int i = 0; i < points.size(); i++) {
         String label = request.anchors().get(i).label();
         residuals.add(new CalibrationDto.AnchorResidual(label == null ? "Точка " + (i + 1) : label,
               round(calibration.residualMeters(points.get(i)))));
      }
      double max = residuals.stream().mapToDouble(CalibrationDto.AnchorResidual::residualM).max().orElse(0);
      return new CalibrationDto(Math.round(calibration.metersPerPx() * 10_000) / 10_000.0, max, residuals);
   }

   // ─────────────────────── схема ───────────────────────

   /**
    * Новая версия схемы: сохраняется целиком, ряды сопоставляются по коду (контейнеры и магазины
    * остаются на своих рядах), ряды, которых нет в новой схеме, выключаются. GPS-матрица переносится.
    */
   @Transactional
   public PublishedMapDto publishMap(MapUploadRequest request) {
      validate(request);
      MapVersion previous = currentVersion();
      BigDecimal metersPerPx = request.metersPerPx() == null ? previous.getMetersPerPx()
            : BigDecimal.valueOf(request.metersPerPx()).setScale(4, RoundingMode.HALF_UP);
      MapVersion next = new MapVersion(versions.maxVersion() + 1, write(request.boundary()),
            write(request.blocks()), write(request.passages()), write(request.entrances()),
            write(orEmpty(request.pois())), write(orEmpty(request.streets())), write(orEmpty(request.labels())),
            metersPerPx, previous.getGeoAffine());
      versions.clearCurrent();
      next.setCurrent(true);
      versions.save(next);

      Set<String> codes = new HashSet<>();
      for (RowUpload upload : request.rows()) {
         codes.add(upload.code());
         rows.findByCode(upload.code()).ifPresentOrElse(
               row -> row.update(upload.label(), upload.type(), write(upload.geometry()), upload.sortOrder().shortValue()),
               () -> rows.save(new MarketRow(upload.code(), upload.label(), upload.type(), write(upload.geometry()),
                     upload.sortOrder().shortValue(), newToken())));
      }
      int deactivated = 0;
      for (MarketRow row : rows.findAll()) {
         if (row.isActive() && !codes.contains(row.getCode())) {
            row.setActive(false);
            deactivated++;
         }
      }
      market.reload();
      return new PublishedMapDto(next.getVersion(), codes.size(), deactivated);
   }

   // ─────────────────────── QR ───────────────────────

   public byte[] qrPng(String token) {
      MarketSnapshot snapshot = market.snapshot();
      if (snapshot.containerByToken(token).isEmpty() && snapshot.rowByToken(token).isEmpty()) {
         throw new NotFoundException("QR_NOT_FOUND", "Такого QR-кода нет");
      }
      return QrCodes.png(qrBaseUrl + token, QR_SIZE);
   }

   /** Лист наклеек для печати из браузера: табличка ряда и наклейки всех его контейнеров. */
   public String qrSheet(Long rowId) {
      MarketSnapshot.RowView row = market.snapshot().row(rowId).orElseThrow(rowNotFound());
      StringBuilder html = new StringBuilder("""
            <!doctype html><html lang="ru"><head><meta charset="utf-8"><title>QR — %s</title>
            <style>body{font-family:Inter,Arial,sans-serif;margin:16px}
            .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
            .card{border:1px dashed #999;border-radius:8px;padding:8px;text-align:center;page-break-inside:avoid}
            .card img{width:100%%;max-width:180px}.title{font-weight:800;font-size:18px}
            .sub{color:#555;font-size:13px}.row{border:2px solid #2155E6}</style></head><body>
            <div class="grid">
            """.formatted(escape(row.row().getLabel())));
      html.append(card(row.row().getQrToken(), row.row().getLabel(), "Табличка в начале ряда", true));
      for (MarketSnapshot.ContainerView container : row.all()) {
         html.append(card(container.container().getQrToken(),
               row.row().getLabel() + " · Бокс " + container.container().getNumber(),
               sideName(container.container().getSide()) + " сторона", false));
      }
      return html.append("</div></body></html>").toString();
   }

   private String card(String token, String title, String subtitle, boolean rowCard) {
      String png = Base64.getEncoder().encodeToString(QrCodes.png(qrBaseUrl + token, QR_SIZE));
      return "<div class=\"card" + (rowCard ? " row" : "") + "\"><img alt=\"QR\" src=\"data:image/png;base64," + png
            + "\"><div class=\"title\">" + escape(title) + "</div><div class=\"sub\">" + escape(subtitle)
            + "</div></div>\n";
   }

   // ─────────────────────── внутреннее ───────────────────────

   private void validate(MapUploadRequest request) {
      try {
         List<List<kg.kudaibergen.market.geo.Point>> lines = new ArrayList<>();
         for (JsonNode passage : request.passages()) {
            lines.add(RowShape.points(passage.get("points")));
         }
         if (lines.isEmpty() || !request.entrances().isArray() || request.entrances().isEmpty()) {
            throw new IllegalArgumentException("Нужны проходы и хотя бы один вход");
         }
         PassageGraph.build(lines);
         for (RowUpload row : request.rows()) {
            RowShape.parse(row.geometry(), row.type());
         }
         if (request.rows().stream().map(RowUpload::code).distinct().count() != request.rows().size()) {
            throw new IllegalArgumentException("Коды рядов повторяются");
         }
      } catch (RuntimeException e) {
         throw new BadRequestException("BAD_MAP", "Схема не принята: " + e.getMessage());
      }
   }

   private MapVersion currentVersion() {
      return versions.findByCurrentTrue().orElseThrow(() -> new IllegalStateException("Нет текущей схемы"));
   }

   private JsonNode orEmpty(JsonNode node) {
      return node == null ? mapper.createArrayNode() : node;
   }

   private String write(Object value) {
      try {
         return mapper.writeValueAsString(value);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException(e);
      }
   }

   private static AdminContainerDto toDto(Container container, MarketRow row) {
      return new AdminContainerDto(container.getId(), row.getId(), row.getCode(), container.getSide(),
            container.getNumber(), container.isActive(), container.getTenantPhone(), container.getQrToken());
   }

   private static java.util.function.Supplier<NotFoundException> rowNotFound() {
      return () -> new NotFoundException("ROW_NOT_FOUND", "Ряд не найден");
   }

   static String newToken() {
      return UUID.randomUUID().toString().replace("-", "");
   }

   private static String sideName(Side side) {
      return switch (side) {
         case NORTH -> "Северная";
         case SOUTH -> "Южная";
         case WEST -> "Западная";
         case EAST -> "Восточная";
      };
   }

   private static String escape(String text) {
      return HtmlUtils.htmlEscape(text);
   }

   private static double round(double value) {
      return Math.round(value * 100) / 100.0;
   }
}
