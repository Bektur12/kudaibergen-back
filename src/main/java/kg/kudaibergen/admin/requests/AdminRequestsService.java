package kg.kudaibergen.admin.requests;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kg.kudaibergen.admin.common.AdminNotices;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.moderation.ContentModeration;
import kg.kudaibergen.admin.requests.AdminRequestDtos.AdminOfferDto;
import kg.kudaibergen.admin.requests.AdminRequestDtos.AdminPartRequestDto;
import kg.kudaibergen.admin.requests.AdminRequestDtos.AdminReplyDto;
import kg.kudaibergen.admin.requests.AdminRequestDtos.AdminServiceCarDto;
import kg.kudaibergen.admin.requests.AdminRequestDtos.AdminServiceRequestDto;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorBuyerDto;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorCounts;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorPeriod;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorRow;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorStatus;
import kg.kudaibergen.admin.requests.AdminRequestDtos.RecipientCountsDto;
import kg.kudaibergen.admin.search.AdminSearchService;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.master.ServiceRequestService;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.request.RequestService;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Мониторинг запросов на запчасти и заявок на услуги [A8]: списки с фильтрами и счётчиками, карточки
 * с ответами и откликами, «Расширить», «Скрыть». Живые изменения — канал Centrifugo admin:requests.
 */
@Service
public class AdminRequestsService {

   static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

   /** Статус для таблицы (см. MonitorStatus). positive — «Есть» / «Могу помочь». */
   static final String PART_STATUS = """
         case when r.hidden_by_admin then 'HIDDEN'
              when r.status = 'CLOSED' and r.closed_with_shop_id is not null then 'AGREED'
              when r.status = 'CLOSED' then 'CLOSED'
              when r.status = 'EXPIRED' and r.have_count = 0 then 'NO_REPLIES'
              when r.status = 'EXPIRED' then 'EXPIRED'
              else 'ACTIVE' end""";
   static final String SERVICE_STATUS = """
         case when r.hidden_by_admin then 'HIDDEN'
              when r.status = 'CLOSED' and r.closed_with_master_id is not null then 'AGREED'
              when r.status = 'CLOSED' then 'CLOSED'
              when r.status = 'EXPIRED' and r.can_help_count = 0 then 'NO_REPLIES'
              when r.status = 'EXPIRED' then 'EXPIRED'
              else 'ACTIVE' end""";

   private final NamedParameterJdbcTemplate jdbc;
   private final RequestService partRequests;
   private final ServiceRequestService serviceRequests;
   private final ContentModeration content;
   private final AdminNotices notices;
   private final MediaService media;
   private final AdminRequestsRealtime realtime;

   public AdminRequestsService(NamedParameterJdbcTemplate jdbc, RequestService partRequests,
                               ServiceRequestService serviceRequests, ContentModeration content,
                               AdminNotices notices, MediaService media, AdminRequestsRealtime realtime) {
      this.jdbc = jdbc;
      this.partRequests = partRequests;
      this.serviceRequests = serviceRequests;
      this.content = content;
      this.notices = notices;
      this.media = media;
      this.realtime = realtime;
   }

   // ─────────────────────── списки ───────────────────────

   @Transactional(readOnly = true)
   public AdminPage<MonitorRow, MonitorCounts> partRequests(MonitorPeriod period, MonitorStatus status,
                                                           boolean noReplies, String q, String cursor, Integer limit) {
      int size = CursorPage.limit(limit);
      MapSqlParameterSource params = filters(period, status, noReplies, q, cursor, size);
      List<MonitorRow> rows = jdbc.query("""
            select r.id, r.text as text, r.created_at, r.expires_at, r.recipients_count as received,
                   r.have_count as positive, u.id as user_id, u.name as user_name, u.phone,
                   b.name || ' ' || m.name || coalesce(' ' || m.generation, '') || ' · ' || r.year as car,
                   (select count(*) from request_recipients rr where rr.request_id = r.id and rr.seen_at is not null) as seen,
                   """ + PART_STATUS + " as monitor_status" + """
             , null as service, false as urgent
              from part_requests r
              join users u on u.id = r.buyer_id
              join brands b on b.id = r.brand_id
              join models m on m.id = r.model_id
             where r.created_at >= :from
               and (cast(:status as text) is null or (""" + PART_STATUS + """
            ) = :status)
               and (not :noReplies or r.have_count = 0)
               and (cast(:like as text) is null or lower(r.text) like :like
                    or (cast(:digits as text) is not null and u.phone like '%' || :digits || '%'))
               and (:after = 0 or r.id < :after)
             order by r.id desc limit :limit""", params, AdminRequestsService::row);
      return page(rows, size, period);
   }

   @Transactional(readOnly = true)
   public AdminPage<MonitorRow, MonitorCounts> serviceRequests(MonitorPeriod period, MonitorStatus status,
                                                              boolean noReplies, String service, String q,
                                                              String cursor, Integer limit) {
      int size = CursorPage.limit(limit);
      MapSqlParameterSource params = filters(period, status, noReplies, q, cursor, size)
            .addValue("service", service == null || service.isBlank() ? null : service, Types.VARCHAR);
      List<MonitorRow> rows = jdbc.query("""
            select r.id, r.description as text, r.created_at, r.expires_at, r.recipients_count as received,
                   r.can_help_count as positive, u.id as user_id, u.name as user_name, u.phone,
                   b.name || coalesce(' ' || m.name || coalesce(' ' || m.generation, ''), '')
                      || coalesce(' · ' || r.year, '') as car,
                   (select count(*) from service_recipients sr where sr.request_id = r.id and sr.seen_at is not null) as seen,
                   """ + SERVICE_STATUS + " as monitor_status" + """
             , r.service, t.urgent
              from service_requests r
              join users u on u.id = r.buyer_id
              join brands b on b.id = r.brand_id
              left join models m on m.id = r.model_id
              join service_types t on t.code = r.service
             where r.created_at >= :from
               and (cast(:status as text) is null or (""" + SERVICE_STATUS + """
            ) = :status)
               and (not :noReplies or r.can_help_count = 0)
               and (cast(:service as text) is null or r.service = :service)
               and (cast(:like as text) is null or lower(r.description) like :like
                    or (cast(:digits as text) is not null and u.phone like '%' || :digits || '%'))
               and (:after = 0 or r.id < :after)
             order by r.id desc limit :limit""", params, AdminRequestsService::row);
      return page(rows, size, period);
   }

   private AdminPage<MonitorRow, MonitorCounts> page(List<MonitorRow> rows, int size, MonitorPeriod period) {
      boolean more = rows.size() > size;
      List<MonitorRow> items = more ? rows.subList(0, size) : rows;
      String next = more ? CursorPage.encode(String.valueOf(items.get(items.size() - 1).id())) : null;
      return new AdminPage<>(items, next, counts(period));
   }

   /** «Без откликов» — ни одного «Есть» / «Могу помочь» (ещё активные тоже: им пора «Расширить»). */
   @Transactional(readOnly = true)
   public MonitorCounts counts(MonitorPeriod period) {
      return jdbc.queryForObject("""
            select (select count(*) from part_requests where created_at >= :from) as parts,
                   (select count(*) from service_requests where created_at >= :from) as services,
                   (select count(*) from part_requests where created_at >= :from and have_count = 0
                       and not hidden_by_admin and status <> 'CLOSED') as parts_no,
                   (select count(*) from service_requests where created_at >= :from and can_help_count = 0
                       and not hidden_by_admin and status <> 'CLOSED') as services_no""",
            new MapSqlParameterSource("from", java.sql.Timestamp.from(from(period))),
            (rs, n) -> new MonitorCounts(rs.getLong("parts"), rs.getLong("services"), rs.getLong("parts_no"),
                  rs.getLong("services_no")));
   }

   // ─────────────────────── карточки ───────────────────────

   @Transactional(readOnly = true)
   public AdminPartRequestDto partRequest(Long id) {
      Map<String, Object> p = Map.of("id", id);
      return jdbc.query("""
            select r.*, u.id as user_id, u.name as user_name, u.phone,
                   b.name || ' ' || m.name || coalesce(' ' || m.generation, '') || ' · ' || r.year as car,
                   """ + PART_STATUS + " as monitor_status" + """
             
              from part_requests r join users u on u.id = r.buyer_id
              join brands b on b.id = r.brand_id join models m on m.id = r.model_id
             where r.id = :id""", p, (rs, n) -> new AdminPartRequestDto(rs.getLong("id"), rs.getString("text"),
            buyer(rs), rs.getString("car"), rs.getString("target"), MonitorStatus.valueOf(rs.getString("monitor_status")),
            rs.getString("duration"), instant(rs, "created_at"), instant(rs, "expires_at"), instant(rs, "closed_at"),
            (Long) rs.getObject("closed_with_shop_id"), rs.getString("hidden_reason"), counts("request_recipients",
            "HAVE", "NOT_HAVE", id), replies(id), media.items(jdbc.queryForList(
            "select media_id from request_photos where request_id = :id order by sort", p, Long.class))))
            .stream().findFirst().orElseThrow(AdminRequestsService::notFound);
   }

   @Transactional(readOnly = true)
   public AdminServiceRequestDto serviceRequest(Long id) {
      Map<String, Object> p = Map.of("id", id);
      return jdbc.query("""
            select r.*, u.id as user_id, u.name as user_name, u.phone, t.urgent,
                   b.name || coalesce(' ' || m.name || coalesce(' ' || m.generation, ''), '')
                      || coalesce(' · ' || r.year, '') as car,
                   """ + SERVICE_STATUS + " as monitor_status" + """
             
              from service_requests r join users u on u.id = r.buyer_id join service_types t on t.code = r.service
              join brands b on b.id = r.brand_id left join models m on m.id = r.model_id
             where r.id = :id""", p, (rs, n) -> new AdminServiceRequestDto(rs.getLong("id"), rs.getString("service"),
            rs.getBoolean("urgent"), new AdminServiceCarDto(rs.getString("car"), rs.getBigDecimal("engine_volume"),
            rs.getString("fuel"), rs.getString("origin")), rs.getString("description"), media.items(jdbc.queryForList(
            "select media_id from service_request_photos where request_id = :id order by sort", p, Long.class)),
            buyer(rs), rs.getString("when_kind"), instant(rs, "at_time"), rs.getString("where_kind"),
            rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("address"), rs.getInt("radius_km"),
            MonitorStatus.valueOf(rs.getString("monitor_status")), rs.getString("duration"), instant(rs, "created_at"),
            instant(rs, "expires_at"), rs.getInt("extended_times"), instant(rs, "closed_at"),
            (Long) rs.getObject("closed_with_master_id"), rs.getString("hidden_reason"),
            counts("service_recipients", "CAN_HELP", "NOT_MINE", id), offers(id)))
            .stream().findFirst().orElseThrow(AdminRequestsService::notFound);
   }

   // ─────────────────────── действия ───────────────────────

   /** «Расширить» запрос на запчасть до всего рынка. */
   @Transactional
   public AdminPartRequestDto widenPart(Long id) {
      partRequests.adminWiden(id);
      return partRequest(id);
   }

   /** «Расширить радиус» заявки: +5 км, пуш новым мастерам. */
   @Transactional
   public AdminServiceRequestDto widenService(Long id) {
      serviceRequests.adminWiden(id, Lang.RU);
      return serviceRequest(id);
   }

   /** «Скрыть заявку»: закрывается, пропадает у продавцов / мастеров; клиенту — пуш с причиной. */
   @Transactional
   public void hide(boolean service, Long id, String reason) {
      Long buyer = jdbc.queryForList("select buyer_id from " + (service ? "service_requests" : "part_requests")
            + " where id = :id", Map.of("id", id), Long.class).stream().findFirst()
            .orElseThrow(AdminRequestsService::notFound);
      content.hideRequest(service, id, reason);
      notices.contentRemoved(List.of(buyer), reason);
      realtime.afterCommit(service ? "SERVICE" : "PART", id, "HIDDEN");
   }

   // ─────────────────────── внутреннее ───────────────────────

   private List<AdminReplyDto> replies(Long requestId) {
      return jdbc.query("""
            select rp.id, rp.shop_id, s.name, rp.answer, rp.condition, rp.price, rp.message, rp.created_at,
                   (extract(epoch from rp.created_at - rr.notified_at) / 60)::int as after_min
              from request_replies rp
              join shops s on s.id = rp.shop_id
              left join request_recipients rr on rr.request_id = rp.request_id and rr.shop_id = rp.shop_id
             where rp.request_id = :id order by rp.created_at""", Map.of("id", requestId),
            (rs, n) -> new AdminReplyDto(rs.getLong("id"), rs.getLong("shop_id"), rs.getString("name"),
                  rs.getString("answer"), rs.getString("condition"), integer(rs, "price"), rs.getString("message"),
                  instant(rs, "created_at"), integer(rs, "after_min")));
   }

   private List<AdminOfferDto> offers(Long requestId) {
      return jdbc.query("""
            select o.id, o.master_id, ms.name, ms.is_mobile, o.answer, o.price_from, o.available_at, o.message, o.created_at,
                   o.hidden_by_admin, (extract(epoch from o.created_at - sr.notified_at) / 60)::int as after_min
              from service_offers o
              join masters ms on ms.id = o.master_id
              left join service_recipients sr on sr.request_id = o.request_id and sr.master_id = o.master_id
             where o.request_id = :id order by o.created_at""", Map.of("id", requestId),
            (rs, n) -> new AdminOfferDto(rs.getLong("id"), rs.getLong("master_id"), rs.getString("name"),
                  rs.getBoolean("is_mobile"), rs.getString("answer"), integer(rs, "price_from"), instant(rs, "available_at"),
                  rs.getString("message"), instant(rs, "created_at"), integer(rs, "after_min"),
                  rs.getBoolean("hidden_by_admin")));
   }

   private RecipientCountsDto counts(String table, String positive, String negative, Long requestId) {
      return jdbc.queryForObject("select count(*) as delivered, count(seen_at) as seen,"
                  + " count(*) filter (where status = :positive) as positive,"
                  + " count(*) filter (where status = :negative) as negative,"
                  + " count(*) filter (where replied_at is null) as silent from " + table + " where request_id = :id",
            Map.of("positive", positive, "negative", negative, "id", requestId),
            (rs, n) -> new RecipientCountsDto(rs.getLong("delivered"), rs.getLong("seen"), rs.getLong("positive"),
                  rs.getLong("negative"), rs.getLong("silent")));
   }

   private static MapSqlParameterSource filters(MonitorPeriod period, MonitorStatus status, boolean noReplies,
                                                String q, String cursor, int size) {
      String query = q == null || q.isBlank() ? null : q.strip();
      return new MapSqlParameterSource()
            .addValue("from", java.sql.Timestamp.from(from(period)))
            .addValue("status", status == null ? null : status.name(), Types.VARCHAR)
            .addValue("noReplies", noReplies)
            .addValue("like", query == null ? null : "%" + query.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                  .replace("%", "\\%").replace("_", "\\_") + "%", Types.VARCHAR)
            .addValue("digits", query == null ? null : AdminSearchService.phoneDigits(query), Types.VARCHAR)
            .addValue("after", CursorPage.afterId(cursor))
            .addValue("limit", size + 1);
   }

   static Instant from(MonitorPeriod period) {
      LocalDate today = LocalDate.now(BISHKEK);
      return switch (period) {
         case TODAY -> today.atStartOfDay(BISHKEK).toInstant();
         case WEEK -> today.minusDays(6).atStartOfDay(BISHKEK).toInstant();
         case MONTH -> today.minusDays(29).atStartOfDay(BISHKEK).toInstant();
         case ALL -> Instant.EPOCH;
      };
   }

   private static MonitorRow row(ResultSet rs, int n) throws SQLException {
      return new MonitorRow(rs.getLong("id"), rs.getString("text"), buyer(rs), instant(rs, "created_at"),
            rs.getString("car"), rs.getInt("received"), rs.getLong("seen"), rs.getInt("positive"),
            MonitorStatus.valueOf(rs.getString("monitor_status")), rs.getString("service"), rs.getBoolean("urgent"),
            instant(rs, "expires_at"));
   }

   private static MonitorBuyerDto buyer(ResultSet rs) throws SQLException {
      return new MonitorBuyerDto(rs.getLong("user_id"), rs.getString("user_name"), rs.getString("phone"));
   }

   private static Integer integer(ResultSet rs, String column) throws SQLException {
      int value = rs.getInt(column);
      return rs.wasNull() ? null : value;
   }

   private static Instant instant(ResultSet rs, String column) throws SQLException {
      var value = rs.getTimestamp(column);
      return value == null ? null : value.toInstant();
   }

   private static NotFoundException notFound() {
      return new NotFoundException("REQUEST_NOT_FOUND", "Запрос не найден");
   }
}
