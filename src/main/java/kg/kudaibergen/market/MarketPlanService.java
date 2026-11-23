package kg.kudaibergen.market;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.market.dto.MapDto;
import kg.kudaibergen.market.entity.Bazaar;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.geo.GeoCalibration;
import kg.kudaibergen.market.geo.Point;
import kg.kudaibergen.market.geo.RowShape;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Карты рынков без контейнеров (Геология, Дордой Моторс, Дордой) — таблица market_plans. Только посмотреть рынок:
 * ряды, проходы, входы, корпуса и «я здесь» по GPS; контейнеров, поиска и маршрута внутри нет.
 * Ответ — тот же {@link MapDto}, что у Кудайбергена: клиент рисует его тем же движком. У рядов нет id в базе —
 * им даются отрицательные (-1, -2, …) по порядку.
 */
@Service
public class MarketPlanService {

   static final Duration TTL = Duration.ofMinutes(1);
   /** Опорная точка дальше этого от подобранной матрицы — точки сняты с ошибкой, карта легла бы криво. */
   static final double MAX_ANCHOR_RESIDUAL_M = 15;

   private final NamedParameterJdbcTemplate jdbc;
   private final ObjectMapper mapper;
   private final Map<Long, CachedPlan> cache = new ConcurrentHashMap<>();

   public MarketPlanService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
      this.jdbc = jdbc;
      this.mapper = mapper;
   }

   private record CachedPlan(Optional<MapDto> map, Instant loadedAt) {
   }

   /** Карта рынка; пусто — карты нет. */
   public Optional<MapDto> plan(long marketId) {
      CachedPlan cached = cache.get(marketId);
      if (cached == null || cached.loadedAt.plus(TTL).isBefore(Instant.now())) {
         cached = new CachedPlan(load(marketId), Instant.now());
         cache.put(marketId, cached);
      }
      return cached.map;
   }

   private Optional<MapDto> load(long marketId) {
      return jdbc.query("select version, data::text as data from market_plans where market_id = :id",
            Map.of("id", marketId), (rs, n) -> build(rs.getInt("version"), read(rs.getString("data")), mapper))
            .stream().findFirst();
   }

   /** Загрузить (заменить) карту рынка — сразу видна в приложениях. Ошибка в схеме — 400 BAD_MAP. */
   @Transactional
   public MapDto save(long marketId, JsonNode data, Long adminId) {
      if (marketId == Bazaar.KUDAIBERGEN_ID) {
         throw new BadRequestException("BAD_MAP", "Схема Кудайбергена — через черновик и публикацию (/admin/market/map)");
      }
      Integer exists = jdbc.queryForObject("select count(*) from markets where id = :id", Map.of("id", marketId),
            Integer.class);
      if (exists == null || exists == 0) {
         throw new kg.kudaibergen.common.error.NotFoundException("MARKET_NOT_FOUND", "Рынок не найден");
      }
      MapDto checked = build(0, data, mapper);
      Integer version = jdbc.queryForObject("""
            insert into market_plans (market_id, version, data, updated_by, updated_at)
            values (:id, 1, cast(:data as jsonb), :admin, now())
            on conflict (market_id) do update set version = market_plans.version + 1, data = excluded.data,
                                                  updated_by = excluded.updated_by, updated_at = now()
            returning version""",
            new MapSqlParameterSource().addValue("id", marketId).addValue("data", data.toString())
                  .addValue("admin", adminId), Integer.class);
      jdbc.update("update markets set has_map = true where id = :id", Map.of("id", marketId));
      cache.remove(marketId);
      return withVersion(checked, version == null ? 1 : version);
   }

   private JsonNode read(String text) {
      try {
         return mapper.readTree(text);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException("Карта рынка повреждена", e);
      }
   }

   private static MapDto withVersion(MapDto map, int version) {
      return new MapDto(version, map.coordinateSystem(), map.boundary(), map.blocks(), map.passages(), map.entrances(),
            map.pois(), map.streets(), map.labels(), map.rows(), map.metersPerPx(), map.geo());
   }

   /** Схема в формате снятия со спутника → карта. Проверяет всё, что нужно для отрисовки; ошибка — 400 BAD_MAP. */
   static MapDto build(int version, JsonNode data, ObjectMapper mapper) {
      try {
         JsonNode boundary = data.path("boundary");
         if (!boundary.isArray() || boundary.size() < 3) {
            throw new IllegalArgumentException("boundary — контур рынка, нужно хотя бы 3 точки");
         }
         RowShape.points(boundary);

         List<GeoCalibration.Anchor> anchors = new ArrayList<>();
         data.path("anchors").forEach(a -> anchors.add(new GeoCalibration.Anchor(a.get("lat").asDouble(),
               a.get("lon").asDouble(), a.get("x").asDouble(), a.get("y").asDouble())));
         GeoCalibration geo = GeoCalibration.fit(anchors);
         for (GeoCalibration.Anchor anchor : anchors) {
            double residual = geo.residualMeters(anchor);
            if (residual > MAX_ANCHOR_RESIDUAL_M) {
               throw new IllegalArgumentException("Опорная точка %.6f, %.6f не сходится с остальными на %.0f м"
                     .formatted(anchor.lat(), anchor.lon(), residual));
            }
         }

         ArrayNode blocks = mapper.createArrayNode();
         for (JsonNode block : data.path("blocks")) {
            ObjectNode out = mapper.createObjectNode();
            out.put("kind", block.path("kind").asText("anon"));
            out.put("label", block.path("label").asText(""));
            if (block.has("polygon")) {
               out.set("polygon", block.get("polygon"));
            } else {
               ObjectNode rect = out.putObject("rect");
               for (String key : List.of("x", "y", "w", "h")) {
                  if (!block.hasNonNull(key)) {
                     throw new IllegalArgumentException("Блок «" + block.path("label").asText() + "» без " + key);
                  }
                  rect.put(key, block.get(key).asDouble());
               }
            }
            blocks.add(out);
         }

         List<MapDto.PassageDto> passages = new ArrayList<>();
         for (JsonNode passage : data.path("passages")) {
            List<Point> points = RowShape.points(passage.path("points"));
            if (points.size() < 2) {
               throw new IllegalArgumentException("Проход — хотя бы 2 точки");
            }
            passages.add(new MapDto.PassageDto(points.stream().map(Point::toArray).toList(),
                  passage.path("width").asDouble(8)));
         }

         List<MapDto.MapRowDto> rows = new ArrayList<>();
         int index = 0;
         for (JsonNode row : data.path("rows")) {
            index++;
            String code = row.path("code").asText("");
            if (code.isBlank()) {
               throw new IllegalArgumentException("Ряд №" + index + " без кода");
            }
            RowType type = RowType.valueOf(row.path("type").asText("ROW"));
            JsonNode geometry = row.path("geometry");
            RowShape shape = RowShape.parse(geometry, type);
            rows.add(new MapDto.MapRowDto((long) -index, code, row.path("label").asText("Ряд " + code), type, geometry,
                  shape.center().toArray(), shape.axis().stream().map(Point::toArray).toList(), shape.halfWidth(),
                  List.of()));
         }

         return new MapDto(version, "пиксели снимка рынка, ось Y вниз", boundary, blocks, passages,
               array(data, "entrances", mapper), array(data, "pois", mapper), array(data, "streets", mapper),
               array(data, "labels", mapper), rows, Math.round(geo.metersPerPx() * 10_000) / 10_000.0, geo);
      } catch (IllegalArgumentException | NullPointerException e) {
         throw new BadRequestException("BAD_MAP", e.getMessage() == null ? "Схема не в том формате" : e.getMessage());
      }
   }

   private static JsonNode array(JsonNode data, String key, ObjectMapper mapper) {
      JsonNode node = data.get(key);
      return node != null && node.isArray() ? node : mapper.createArrayNode();
   }
}
