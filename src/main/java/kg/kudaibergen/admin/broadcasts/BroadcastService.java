package kg.kudaibergen.admin.broadcasts;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.admin.broadcasts.BroadcastAudience.AudienceQuery;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.Audience;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastCounts;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastDto;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastFilters;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastInput;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastStatus;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.EstimateDto;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Рассылки [A6]: оценка аудитории, черновик, планирование, отмена, история. Отправляет BroadcastSender.
 * Тихие часы 22:00–07:00 (Бишкек): время отправки в них переносится на 07:00.
 */
@Service
public class BroadcastService {

   static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
   static final LocalTime QUIET_FROM = LocalTime.of(22, 0);
   static final LocalTime QUIET_TO = LocalTime.of(7, 0);

   private final NamedParameterJdbcTemplate jdbc;
   private final BroadcastAudience audiences;
   private final ObjectMapper json;
   private final Clock clock;

   public BroadcastService(NamedParameterJdbcTemplate jdbc, BroadcastAudience audiences, ObjectMapper json,
                           Clock clock) {
      this.jdbc = jdbc;
      this.audiences = audiences;
      this.json = json;
      this.clock = clock;
   }

   @Transactional(readOnly = true)
   public EstimateDto estimate(Audience audience, BroadcastFilters filters) {
      AudienceQuery query = audiences.of(audience, filters);
      Map<String, Object> row = jdbc.queryForMap("""
            select count(*) as recipients,
                   count(*) filter (where exists (select 1 from device_tokens d where d.user_id = a.id)) as devices
            from (""" + query.sql() + ") a", query.params());
      long recipients = ((Number) row.get("recipients")).longValue();
      return new EstimateDto(recipients, ((Number) row.get("devices")).longValue(),
            BroadcastAudience.label(audience, recipients));
   }

   @Transactional
   public BroadcastDto create(BroadcastInput input, Long adminId) {
      audiences.of(input.audience(), input.filters());
      Long id = jdbc.queryForObject("""
            insert into broadcasts (audience, filters, title_ru, title_kg, body_ru, body_kg, created_by)
            values (:audience, cast(:filters as jsonb), :titleRu, :titleKg, :bodyRu, :bodyKg, :admin) returning id""",
            params(input).addValue("admin", adminId), Long.class);
      return get(id);
   }

   @Transactional
   public BroadcastDto update(Long id, BroadcastInput input) {
      requireStatus(id, BroadcastStatus.DRAFT, "Менять можно только черновик");
      audiences.of(input.audience(), input.filters());
      jdbc.update("""
            update broadcasts set audience = :audience, filters = cast(:filters as jsonb), title_ru = :titleRu,
                   title_kg = :titleKg, body_ru = :bodyRu, body_kg = :bodyKg, updated_at = now()
            where id = :id""", params(input).addValue("id", id));
      return get(id);
   }

   /** Запланировать (или «сейчас»): время в тихих часах переносится на ближайшие 07:00. */
   @Transactional
   public BroadcastDto schedule(Long id, Instant at, boolean now) {
      BroadcastStatus status = status(id);
      if (status != BroadcastStatus.DRAFT && status != BroadcastStatus.SCHEDULED) {
         throw new ConflictException("BROADCAST_STARTED", "Рассылка уже отправляется или закрыта");
      }
      Instant requested = now || at == null || at.isBefore(clock.instant()) ? clock.instant() : at;
      Instant when = outsideQuietHours(requested);
      jdbc.update("""
            update broadcasts set status = 'SCHEDULED', scheduled_at = :at, updated_at = now() where id = :id""",
            new MapSqlParameterSource("id", id).addValue("at", Timestamp.from(when)));
      BroadcastDto dto = get(id);
      return withDeferred(dto, !when.equals(requested));
   }

   /** Отменить черновик, запланированную или ещё идущую (оставшиеся не получат). */
   @Transactional
   public BroadcastDto cancel(Long id) {
      BroadcastStatus status = status(id);
      if (status == BroadcastStatus.SENT || status == BroadcastStatus.CANCELLED) {
         throw new ConflictException("BROADCAST_FINISHED", "Рассылка уже завершена");
      }
      jdbc.update("update broadcasts set status = 'CANCELLED', updated_at = now() where id = :id", Map.of("id", id));
      return get(id);
   }

   @Transactional(readOnly = true)
   public AdminPage<BroadcastDto, BroadcastCounts> list(BroadcastStatus status, String cursor, Integer limit) {
      int size = CursorPage.limit(limit);
      MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("status", status == null ? null : status.name(), Types.VARCHAR)
            .addValue("after", CursorPage.afterId(cursor)).addValue("limit", size + 1);
      List<BroadcastDto> rows = jdbc.query(SELECT + """
             where (cast(:status as text) is null or b.status = :status) and (:after = 0 or b.id < :after)
             order by b.id desc limit :limit""", p, this::map);
      boolean more = rows.size() > size;
      List<BroadcastDto> page = more ? rows.subList(0, size) : rows;
      BroadcastCounts counts = jdbc.queryForObject("""
            select count(*) as all_count, count(*) filter (where status = 'DRAFT') as drafts,
                   count(*) filter (where status in ('SCHEDULED', 'SENDING')) as scheduled,
                   count(*) filter (where status = 'SENT') as sent from broadcasts""", Map.of(),
            (rs, n) -> new BroadcastCounts(rs.getLong("all_count"), rs.getLong("drafts"), rs.getLong("scheduled"),
                  rs.getLong("sent")));
      return new AdminPage<>(page, more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1).id())) : null,
            counts);
   }

   @Transactional(readOnly = true)
   public BroadcastDto get(Long id) {
      return jdbc.query(SELECT + " where b.id = :id", Map.of("id", id), this::map).stream().findFirst()
            .orElseThrow(() -> new NotFoundException("BROADCAST_NOT_FOUND", "Рассылка не найдена"));
   }

   /** Отметка «открыл из пуша» (приложение): один раз на получателя. */
   @Transactional
   public void opened(Long broadcastId, Long userId) {
      int changed = jdbc.update("""
            update broadcast_recipients set opened_at = now()
            where broadcast_id = :id and user_id = :user and opened_at is null""",
            Map.of("id", broadcastId, "user", userId));
      if (changed > 0) {
         jdbc.update("update broadcasts set opened_count = opened_count + 1 where id = :id", Map.of("id", broadcastId));
      }
   }

   /** Ближайший момент вне тихих часов (по Бишкеку): 22:00–07:00 → 07:00. */
   static Instant outsideQuietHours(Instant at) {
      ZonedDateTime local = at.atZone(BISHKEK);
      LocalTime time = local.toLocalTime();
      if (!time.isBefore(QUIET_FROM)) {
         return LocalDate.from(local).plusDays(1).atTime(QUIET_TO).atZone(BISHKEK).toInstant();
      }
      if (time.isBefore(QUIET_TO)) {
         return LocalDate.from(local).atTime(QUIET_TO).atZone(BISHKEK).toInstant();
      }
      return at;
   }

   static boolean quiet(Instant at) {
      return !outsideQuietHours(at).equals(at);
   }

   // ─────────────────────── внутреннее ───────────────────────

   private static final String SELECT = """
         select b.*, b.filters::text as filters_json, u.name as creator from broadcasts b
           left join users u on u.id = b.created_by
         """;

   private BroadcastDto map(ResultSet rs, int n) throws SQLException {
      int delivered = rs.getInt("delivered_count");
      int opened = rs.getInt("opened_count");
      return new BroadcastDto(rs.getLong("id"), Audience.valueOf(rs.getString("audience")),
            filters(rs.getString("filters_json")), rs.getString("title_ru"), rs.getString("title_kg"),
            rs.getString("body_ru"), rs.getString("body_kg"), BroadcastStatus.valueOf(rs.getString("status")),
            instant(rs, "scheduled_at"), instant(rs, "started_at"), instant(rs, "sent_at"),
            rs.getInt("recipients_count"), delivered, opened,
            delivered == 0 ? null : (int) Math.round(opened * 100.0 / delivered), (Long) rs.getObject("created_by"),
            rs.getString("creator"), instant(rs, "created_at"), false);
   }

   private static BroadcastDto withDeferred(BroadcastDto d, boolean deferred) {
      return new BroadcastDto(d.id(), d.audience(), d.filters(), d.titleRu(), d.titleKg(), d.bodyRu(), d.bodyKg(),
            d.status(), d.scheduledAt(), d.startedAt(), d.sentAt(), d.recipients(), d.delivered(), d.opened(),
            d.openedPct(), d.createdBy(), d.createdByName(), d.createdAt(), deferred);
   }

   private MapSqlParameterSource params(BroadcastInput input) {
      return new MapSqlParameterSource()
            .addValue("audience", input.audience().name())
            .addValue("filters", write(input.filters() == null ? BroadcastFilters.none() : input.filters()))
            .addValue("titleRu", input.titleRu().strip())
            .addValue("titleKg", blank(input.titleKg()), Types.VARCHAR)
            .addValue("bodyRu", input.bodyRu().strip())
            .addValue("bodyKg", blank(input.bodyKg()), Types.VARCHAR);
   }

   private BroadcastStatus status(Long id) {
      return jdbc.queryForList("select status from broadcasts where id = :id", Map.of("id", id), String.class)
            .stream().findFirst().map(BroadcastStatus::valueOf)
            .orElseThrow(() -> new NotFoundException("BROADCAST_NOT_FOUND", "Рассылка не найдена"));
   }

   private void requireStatus(Long id, BroadcastStatus expected, String message) {
      if (status(id) != expected) {
         throw new ConflictException("BROADCAST_NOT_DRAFT", message);
      }
   }

   BroadcastFilters filters(String value) {
      try {
         return value == null ? BroadcastFilters.none() : json.readValue(value, BroadcastFilters.class);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException(e);
      }
   }

   private String write(Object value) {
      try {
         return json.writeValueAsString(value);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException(e);
      }
   }

   private static String blank(String value) {
      return value == null || value.isBlank() ? null : value.strip();
   }

   private static Instant instant(ResultSet rs, String column) throws SQLException {
      Timestamp value = rs.getTimestamp(column);
      return value == null ? null : value.toInstant();
   }
}
