package kg.kudaibergen.stats;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Счётчики статистики бокса (17) прямо из рабочих таблиц: запросов у бокса — сотни в месяц,
 * агрегаты по индексам (shop_id, notified_at) и (shop_id, …) считаются быстро. Если станет медленно —
 * дневные агрегаты shop_stats_daily (BACKEND_DESIGN, раздел stats).
 */
@Repository
public class ShopStatsRepository {

   private final NamedParameterJdbcTemplate jdbc;

   public ShopStatsRepository(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   /** Запросы, ответы и среднее время ответа — по запросам, пришедшим за период. */
   public RequestCounts requests(Long shopId, Instant since) {
      return jdbc.queryForObject("""
            select count(*) as received,
                   count(*) filter (where rr.status = 'HAVE') as have,
                   count(*) filter (where rr.status = 'NOT_HAVE') as not_have,
                   count(*) filter (where rr.replied_at is null and r.status <> 'ACTIVE') as missed,
                   avg(extract(epoch from rr.replied_at - rr.notified_at))
                       filter (where rr.replied_at is not null)::float8 as avg_reply_sec
            from request_recipients rr
            join part_requests r on r.id = rr.request_id
            where rr.shop_id = :shopId and rr.notified_at >= :since""",
            params(shopId, since),
            (rs, i) -> {
               double avg = rs.getDouble("avg_reply_sec");
               return new RequestCounts(rs.getInt("received"), rs.getInt("have"), rs.getInt("not_have"),
                     rs.getInt("missed"), rs.wasNull() ? null : avg);
            });
   }

   public record RequestCounts(int received, int have, int notHave, int missed, Double avgReplySeconds) {
   }

   /** «Написали вам в чат»: первый раз покупатель написал в этот период. */
   public int wroteInChat(Long shopId, Instant since) {
      return count("""
            select count(*) from chats
            where shop_id = :shopId and buyer_first_message_at >= :since""", shopId, since);
   }

   /** «Покупатели подошли»: чаты, где покупатель нажал «Я на месте». */
   public int buyersArrived(Long shopId, Instant since) {
      return count("""
            select count(distinct m.chat_id) from messages m
            join chats c on c.id = m.chat_id
            where c.shop_id = :shopId and m.side = 'BUYER' and m.type = 'QUICK' and m.code = 'ARRIVED'
              and m.created_at >= :since""", shopId, since);
   }

   /** «Продажи»: покупатель закрыл запрос «Купил» у этого бокса. */
   public int sales(Long shopId, Instant since) {
      return count("""
            select count(*) from part_requests
            where closed_with_shop_id = :shopId and closed_at >= :since""", shopId, since);
   }

   /** Чаще всего спрашивали: категории пришедших запросов (если покупатель выбрал чип). */
   public List<CategoryCount> topCategories(Long shopId, Instant since, int limit) {
      Map<String, Object> params = Map.of("shopId", shopId, "since", Timestamp.from(since), "limit", limit);
      return jdbc.query("""
            select r.category_id, count(*) as requests
            from request_recipients rr
            join part_requests r on r.id = rr.request_id
            where rr.shop_id = :shopId and rr.notified_at >= :since and r.category_id is not null
            group by r.category_id
            order by requests desc, r.category_id
            limit :limit""", params,
            (rs, i) -> new CategoryCount(rs.getLong("category_id"), rs.getInt("requests")));
   }

   public record CategoryCount(Long categoryId, int count) {
   }

   private int count(String sql, Long shopId, Instant since) {
      Integer value = jdbc.queryForObject(sql, params(shopId, since), Integer.class);
      return value == null ? 0 : value;
   }

   private static Map<String, Object> params(Long shopId, Instant since) {
      return Map.of("shopId", shopId, "since", Timestamp.from(since));
   }
}
