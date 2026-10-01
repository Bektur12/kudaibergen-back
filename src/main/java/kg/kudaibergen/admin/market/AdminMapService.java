package kg.kudaibergen.admin.market;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.market.AdminMapDtos.AdminMapDto;
import kg.kudaibergen.admin.market.AdminMapDtos.AdminRowDetailDto;
import kg.kudaibergen.admin.market.AdminMapDtos.AdminRowDto;
import kg.kudaibergen.admin.market.AdminMapDtos.GeoAnchorItem;
import kg.kudaibergen.admin.market.AdminMapDtos.GeoAnchorsDto;
import kg.kudaibergen.admin.market.AdminMapDtos.GridCellDto;
import kg.kudaibergen.admin.market.AdminMapDtos.GridShopDto;
import kg.kudaibergen.admin.market.AdminMapDtos.GridSideDto;
import kg.kudaibergen.admin.market.AdminMapDtos.MapDraftDto;
import kg.kudaibergen.admin.market.AdminMapDtos.MapVersionInfo;
import kg.kudaibergen.admin.market.AdminMapDtos.RowCounts;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.market.admin.AdminMarketDtos.MapUploadRequest;
import kg.kudaibergen.market.admin.AdminMarketDtos.PublishedMapDto;
import kg.kudaibergen.market.admin.MarketAdminService;
import kg.kudaibergen.market.entity.MapVersion;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Рынок и карта [A3]: черновик схемы и публикация, ряды со счётчиками и сеткой мест, опорные GPS-точки.
 * Публикация — тот же путь, что PUT /admin/market/map: новая версия, клиенты перекачивают карту.
 */
@Service
public class AdminMapService {

   private final NamedParameterJdbcTemplate jdbc;
   private final MarketAdminService market;
   private final ObjectMapper json;

   public AdminMapService(NamedParameterJdbcTemplate jdbc, MarketAdminService market, ObjectMapper json) {
      this.jdbc = jdbc;
      this.market = market;
      this.json = json;
   }

   // ─────────────────────── схема ───────────────────────

   @Transactional(readOnly = true)
   public AdminMapDto map() {
      MapVersionInfo current = currentInfo();
      return new AdminMapDto(current, market.exportCurrent(), draft(current.version()).orElse(null));
   }

   /** Сохранить черновик. Схема проверяется, но сохраняется и с ошибкой — её видно в valid / error. */
   @Transactional
   public AdminMapDto saveDraft(MapUploadRequest data, Long adminId) {
      jdbc.update("""
            insert into map_drafts (id, data, based_on_version, updated_by, updated_at)
            values (1, cast(:data as jsonb), :version, :admin, now())
            on conflict (id) do update set data = excluded.data, based_on_version = excluded.based_on_version,
                                           updated_by = excluded.updated_by, updated_at = now()""",
            new MapSqlParameterSource().addValue("data", write(data)).addValue("version", market.current().getVersion())
                  .addValue("admin", adminId));
      return map();
   }

   @Transactional
   public void discardDraft() {
      jdbc.update("delete from map_drafts", Map.of());
   }

   /** Опубликовать черновик: новая версия схемы, черновик удаляется. Ошибка схемы — 400 BAD_MAP. */
   @Transactional
   public PublishedMapDto publish(Long adminId) {
      MapUploadRequest data = rawDraft().orElseThrow(() ->
            new ConflictException("NO_DRAFT", "Черновика нет — сначала сохраните схему"));
      PublishedMapDto published = market.publishMap(data, adminId);
      discardDraft();
      return published;
   }

   private Optional<MapDraftDto> draft(int currentVersion) {
      return jdbc.query("""
            select d.data::text as data, d.based_on_version, d.updated_at, d.updated_by, u.name
              from map_drafts d left join users u on u.id = d.updated_by""", Map.of(), (rs, n) -> {
         MapUploadRequest data = read(rs.getString("data"));
         Optional<String> error = market.check(data);
         return new MapDraftDto(rs.getInt("based_on_version"), rs.getInt("based_on_version") != currentVersion,
               rs.getTimestamp("updated_at").toInstant(), (Long) rs.getObject("updated_by"), rs.getString("name"),
               error.isEmpty(), error.orElse(null), data);
      }).stream().findFirst();
   }

   private Optional<MapUploadRequest> rawDraft() {
      return jdbc.queryForList("select data::text from map_drafts", Map.of(), String.class).stream()
            .findFirst().map(this::read);
   }

   private MapVersionInfo currentInfo() {
      MapVersion current = market.current();
      String name = current.getPublishedBy() == null ? null : jdbc.queryForList(
            "select name from users where id = :id", Map.of("id", current.getPublishedBy()), String.class).stream()
            .findFirst().orElse(null);
      Long rows = jdbc.queryForObject("select count(*) from market_rows where is_active", Map.of(), Long.class);
      return new MapVersionInfo(current.getVersion(), current.getPublishedAt(), current.getPublishedBy(), name,
            rows == null ? 0 : rows.intValue(), current.getMetersPerPx().doubleValue(), current.getGeoAffine() != null);
   }

   // ─────────────────────── ряды ───────────────────────

   private static final String ROWS = """
         select r.id, r.code, r.label, r.type, r.is_active, r.sort_order,
                count(c.id) filter (where c.is_active) as containers,
                count(c.id) filter (where c.is_active and exists (
                   select 1 from shops s where s.container_id = c.id and s.status <> 'REJECTED')) as with_shop,
                count(c.id) filter (where not c.is_active) as disabled,
                count(c.id) filter (where c.is_active and c.side = 'NORTH') as north,
                count(c.id) filter (where c.is_active and c.side = 'SOUTH') as south,
                count(c.id) filter (where c.is_active and c.side = 'WEST') as west,
                count(c.id) filter (where c.is_active and c.side = 'EAST') as east
           from market_rows r
           left join containers c on c.row_id = r.id
         """;

   @Transactional(readOnly = true)
   public AdminPage<AdminRowDto, RowCounts> rows(boolean includeInactive) {
      List<AdminRowDto> items = jdbc.query(ROWS + """
             where :all or r.is_active
             group by r.id
             order by r.sort_order, r.code""", Map.of("all", includeInactive), AdminMapService::row);
      List<AdminRowDto> active = items.stream().filter(AdminRowDto::active).toList();
      return new AdminPage<>(items, null, new RowCounts(active.size(),
            active.stream().mapToLong(AdminRowDto::containers).sum(),
            active.stream().mapToLong(AdminRowDto::withShop).sum(),
            active.stream().mapToLong(AdminRowDto::free).sum()));
   }

   @Transactional(readOnly = true)
   public AdminRowDetailDto row(Long rowId) {
      AdminRowDto row = jdbc.query(ROWS + " where r.id = :id group by r.id", Map.of("id", rowId), AdminMapService::row)
            .stream().findFirst().orElseThrow(() -> new NotFoundException("ROW_NOT_FOUND", "Ряд не найден"));
      Map<Side, List<GridCellDto>> bySide = new LinkedHashMap<>();
      for (Side side : Side.of(row.type())) {
         bySide.put(side, new ArrayList<>());
      }
      jdbc.query("""
            select c.id, c.side, c.number, c.pos_in_row, c.is_active, c.tenant_name, c.tenant_phone, c.qr_token,
                   s.id as shop_id, s.name as shop_name, s.status as shop_status,
                   p.id as incoming_id, p.name as incoming_name, p.status as incoming_status
              from containers c
              left join shops s on s.container_id = c.id and s.status <> 'REJECTED'
              left join shops p on p.pending_container_id = c.id and p.status <> 'REJECTED'
             where c.row_id = :id
             order by c.side, c.pos_in_row, c.number""", Map.of("id", rowId), rs -> {
         Side side = Side.valueOf(rs.getString("side"));
         bySide.computeIfAbsent(side, key -> new ArrayList<>()).add(new GridCellDto(rs.getLong("id"), side,
               rs.getInt("number"), rs.getInt("pos_in_row"), rs.getBoolean("is_active"), rs.getString("tenant_name"),
               rs.getString("tenant_phone"), shop(rs, "shop"), shop(rs, "incoming"), rs.getString("qr_token")));
      });
      List<GridSideDto> sides = bySide.entrySet().stream().map(e -> new GridSideDto(e.getKey(), e.getValue())).toList();
      return new AdminRowDetailDto(row, sides);
   }

   // ─────────────────────── GPS ───────────────────────

   @Transactional(readOnly = true)
   public GeoAnchorsDto geoAnchors() {
      MapVersion current = market.current();
      List<GeoAnchorItem> anchors = jdbc.query("select id, lat, lon, x, y, label from geo_anchors order by id",
            Map.of(), (rs, n) -> new GeoAnchorItem(rs.getLong("id"), rs.getDouble("lat"), rs.getDouble("lon"),
                  rs.getDouble("x"), rs.getDouble("y"), rs.getString("label")));
      return new GeoAnchorsDto(anchors, current.getGeoAffine() != null, current.getMetersPerPx().doubleValue());
   }

   // ─────────────────────── внутреннее ───────────────────────

   private static GridShopDto shop(ResultSet rs, String prefix) throws SQLException {
      Object id = rs.getObject(prefix + "_id");
      return id == null ? null : new GridShopDto(((Number) id).longValue(), rs.getString(prefix + "_name"),
            rs.getString(prefix + "_status"));
   }

   private static AdminRowDto row(ResultSet rs, int n) throws SQLException {
      Map<Side, Long> bySide = new EnumMap<>(Side.class);
      RowType type = RowType.valueOf(rs.getString("type"));
      for (Side side : Side.of(type)) {
         bySide.put(side, rs.getLong(side.name().toLowerCase()));
      }
      long containers = rs.getLong("containers");
      long withShop = rs.getLong("with_shop");
      return new AdminRowDto(rs.getLong("id"), rs.getString("code"), rs.getString("label"), type,
            rs.getBoolean("is_active"), rs.getInt("sort_order"), containers, withShop, containers - withShop,
            rs.getLong("disabled"), bySide);
   }

   private MapUploadRequest read(String value) {
      try {
         return json.readValue(value, MapUploadRequest.class);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException("Испорчен черновик схемы", e);
      }
   }

   private String write(MapUploadRequest value) {
      try {
         return json.writeValueAsString(value);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException(e);
      }
   }
}
