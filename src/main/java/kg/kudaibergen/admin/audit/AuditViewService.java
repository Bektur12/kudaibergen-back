package kg.kudaibergen.admin.audit;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.admin.audit.AuditViewDtos.AuditCounts;
import kg.kudaibergen.admin.audit.AuditViewDtos.AuditEntryDto;
import kg.kudaibergen.admin.audit.AuditViewDtos.AuditRow;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Журнал: фильтры по сотруднику, действию, объекту и периоду; новые сверху. */
@Service
public class AuditViewService {

   static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

   private static final String WHERE = """
          where (cast(:admin as bigint) is null or a.admin_id = :admin)
            and (cast(:action as text) is null or a.action = :action)
            and (cast(:entityType as text) is null or a.entity_type = :entityType)
            and (cast(:entityId as bigint) is null or a.entity_id = :entityId)
            and (cast(:from as timestamptz) is null or a.created_at >= :from)
            and (cast(:to as timestamptz) is null or a.created_at < :to)
         """;

   private final NamedParameterJdbcTemplate jdbc;
   private final ObjectMapper json;

   public AuditViewService(NamedParameterJdbcTemplate jdbc, ObjectMapper json) {
      this.jdbc = jdbc;
      this.json = json;
   }

   @Transactional(readOnly = true)
   public AdminPage<AuditRow, AuditCounts> list(Long adminId, String action, String entityType, Long entityId,
                                                Instant from, Instant to, String cursor, Integer limit) {
      int size = CursorPage.limit(limit);
      MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("admin", adminId, Types.BIGINT)
            .addValue("action", blank(action), Types.VARCHAR)
            .addValue("entityType", blank(entityType), Types.VARCHAR)
            .addValue("entityId", entityId, Types.BIGINT)
            .addValue("from", from == null ? null : Timestamp.from(from), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("to", to == null ? null : Timestamp.from(to), Types.TIMESTAMP_WITH_TIMEZONE)
            .addValue("after", CursorPage.afterId(cursor))
            .addValue("limit", size + 1)
            .addValue("today", Timestamp.from(LocalDate.now(BISHKEK).atStartOfDay(BISHKEK).toInstant()))
            .addValue("week", Timestamp.from(LocalDate.now(BISHKEK).minusDays(6).atStartOfDay(BISHKEK).toInstant()));
      List<AuditRow> rows = jdbc.query("""
            select a.id, a.admin_id, coalesce(m.full_name, u.name) as admin_name, a.action, a.entity_type, a.entity_id,
                   a.comment, a.ip, a.created_at
              from admin_audit_log a
              left join users u on u.id = a.admin_id
              left join admin_members m on m.user_id = a.admin_id""" + WHERE + """
               and (:after = 0 or a.id < :after)
             order by a.id desc limit :limit""", p, (rs, n) -> new AuditRow(rs.getLong("id"),
            (Long) rs.getObject("admin_id"), rs.getString("admin_name"), rs.getString("action"),
            rs.getString("entity_type"), (Long) rs.getObject("entity_id"), rs.getString("comment"), rs.getString("ip"),
            rs.getTimestamp("created_at").toInstant()));
      boolean more = rows.size() > size;
      List<AuditRow> page = more ? rows.subList(0, size) : rows;
      AuditCounts counts = jdbc.queryForObject("""
            select count(*) filter (where a.created_at >= :today) as today,
                   count(*) filter (where a.created_at >= :week) as week
              from admin_audit_log a""" + WHERE, p,
            (rs, n) -> new AuditCounts(rs.getLong("today"), rs.getLong("week")));
      return new AdminPage<>(page, more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1).id())) : null,
            counts);
   }

   @Transactional(readOnly = true)
   public AuditEntryDto get(Long id) {
      return jdbc.query("""
            select a.*, a.before::text as before_json, a.after::text as after_json,
                   coalesce(m.full_name, u.name) as admin_name
              from admin_audit_log a
              left join users u on u.id = a.admin_id
              left join admin_members m on m.user_id = a.admin_id
             where a.id = :id""", Map.of("id", id), (rs, n) -> new AuditEntryDto(rs.getLong("id"),
            (Long) rs.getObject("admin_id"), rs.getString("admin_name"), rs.getString("action"),
            rs.getString("entity_type"), (Long) rs.getObject("entity_id"), tree(rs.getString("before_json")),
            tree(rs.getString("after_json")), rs.getString("comment"), rs.getString("ip"),
            rs.getTimestamp("created_at").toInstant())).stream().findFirst()
            .orElseThrow(() -> new NotFoundException("AUDIT_NOT_FOUND", "Запись журнала не найдена"));
   }

   /** Какие действия и типы объектов есть в журнале — для выпадающих фильтров. */
   @Transactional(readOnly = true)
   public Map<String, List<String>> facets() {
      return Map.of(
            "actions", jdbc.queryForList("select distinct action from admin_audit_log order by 1", Map.of(), String.class),
            "entityTypes", jdbc.queryForList("select distinct entity_type from admin_audit_log order by 1", Map.of(),
                  String.class));
   }

   private JsonNode tree(String value) {
      if (value == null) {
         return null;
      }
      try {
         return json.readTree(value);
      } catch (JsonProcessingException e) {
         return json.getNodeFactory().textNode(value);
      }
   }

   private static String blank(String value) {
      return value == null || value.isBlank() ? null : value.strip();
   }
}
