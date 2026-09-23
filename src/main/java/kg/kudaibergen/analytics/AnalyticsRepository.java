package kg.kudaibergen.analytics;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.analytics.dto.CategoryDemand;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Метрики считаются на лету из request_recipients + offers по индексу idx_recipients_store.
 * Отдельный трекинг не нужен; когда начнёт тормозить — ночной джоб в store_daily_stats.
 */
@Repository
public class AnalyticsRepository {

   private static final String SUMMARY_SQL = """
         SELECT
             COUNT(*)                                            AS requests_received,
             COUNT(*) FILTER (WHERE rr.replied_at IS NOT NULL)   AS requests_answered,
             COUNT(*) FILTER (WHERE rr.replied_at IS NULL
                                AND r.status = 'EXPIRED')        AS requests_missed,
             COALESCE(SUM(r.budget_max) FILTER (WHERE rr.replied_at IS NULL
                                AND r.status = 'EXPIRED'), 0)    AS missed_budget_sum,
             AVG(EXTRACT(EPOCH FROM (rr.replied_at - rr.notified_at)) / 60)
                 FILTER (WHERE rr.replied_at IS NOT NULL)        AS avg_response_minutes
         FROM request_recipients rr
         JOIN requests r ON r.id = rr.request_id
         WHERE rr.store_id = :storeId
           AND rr.notified_at >= :from
         """;

   private static final String CATEGORIES_SQL = """
         SELECT r.category,
                COUNT(*)                                          AS requests,
                COUNT(*) FILTER (WHERE rr.replied_at IS NOT NULL) AS answered
         FROM request_recipients rr
         JOIN requests r ON r.id = rr.request_id
         WHERE rr.store_id = :storeId
           AND rr.notified_at >= :from
         GROUP BY r.category
         ORDER BY requests DESC
         """;

   private static final String DEALS_SQL = """
         SELECT COUNT(*) FROM chats
         WHERE store_id = :storeId
           AND created_at >= :from
         """;

   private final NamedParameterJdbcTemplate jdbc;

   public AnalyticsRepository(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   public Summary summary(Long storeId, Instant from) {
      return jdbc.queryForObject(SUMMARY_SQL, params(storeId, from), (rs, rowNum) -> {
         double avg = rs.getDouble("avg_response_minutes");
         Integer avgMinutes = rs.wasNull() ? null : (int) Math.round(avg);
         return new Summary(rs.getLong("requests_received"), rs.getLong("requests_answered"),
               rs.getLong("requests_missed"), rs.getLong("missed_budget_sum"), avgMinutes);
      });
   }

   public List<CategoryDemand> categories(Long storeId, Instant from) {
      return jdbc.query(CATEGORIES_SQL, params(storeId, from), (rs, rowNum) ->
            new CategoryDemand(rs.getString("category"), rs.getLong("requests"), rs.getLong("answered")));
   }

   public long dealsClosed(Long storeId, Instant from) {
      Long count = jdbc.queryForObject(DEALS_SQL, params(storeId, from), Long.class);
      return count == null ? 0 : count;
   }

   private Map<String, Object> params(Long storeId, Instant from) {
      return Map.of("storeId", storeId, "from", Timestamp.from(from));
   }

   public record Summary(long requestsReceived, long requestsAnswered, long requestsMissed,
                         long missedBudgetSum, Integer avgResponseMinutes) {
   }
}
