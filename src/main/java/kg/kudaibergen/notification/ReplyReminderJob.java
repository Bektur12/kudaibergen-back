package kg.kudaibergen.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.master.ServiceNotifier;
import kg.kudaibergen.request.RequestNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Раз в минуту: бокс или мастер не ответил на запрос — напоминание «Ответьте покупателю», не чаще раза в 10 минут
 * и не больше {@link #MAX} раз на запрос (через 10, 20 и 30 минут). С полуночи до 7 утра не напоминаем.
 * Счётчик растёт тем же UPDATE, так что лишнего (и с двух инстансов) не придёт. Окно — запросы за последний час:
 * после выкладки не будим старые.
 */
@Component
public class ReplyReminderJob {

   private static final Logger log = LoggerFactory.getLogger(ReplyReminderJob.class);
   static final Duration EVERY = Duration.ofMinutes(10);
   static final Duration WINDOW = Duration.ofHours(1);
   static final int MAX = 3;
   /** С 00:00 до 7:00 продавцов не будим. */
   static final LocalTime NIGHT_END = LocalTime.of(7, 0);

   private final NamedParameterJdbcTemplate jdbc;
   private final RequestNotifier requestNotifier;
   private final ServiceNotifier serviceNotifier;
   private final Clock clock;

   public ReplyReminderJob(NamedParameterJdbcTemplate jdbc, RequestNotifier requestNotifier,
                           ServiceNotifier serviceNotifier, Clock clock) {
      this.jdbc = jdbc;
      this.requestNotifier = requestNotifier;
      this.serviceNotifier = serviceNotifier;
      this.clock = clock;
   }

   @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
   public void run() {
      if (LocalTime.now(clock).isBefore(NIGHT_END)) {
         return;
      }
      Instant now = clock.instant();
      MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("now", java.sql.Timestamp.from(now))
            .addValue("due", java.sql.Timestamp.from(now.minus(EVERY)))
            .addValue("max", MAX)
            .addValue("since", java.sql.Timestamp.from(now.minus(WINDOW)));

      Map<Due, List<Long>> shops = due("""
            update request_recipients rr set reminded_at = :now, reminder_count = rr.reminder_count + 1
            from part_requests pr
            where pr.id = rr.request_id and pr.status = 'ACTIVE' and pr.expires_at > :now
              and rr.status in ('DELIVERED', 'SEEN') and rr.replied_at is null and rr.reminder_count < :max
              and coalesce(rr.reminded_at, rr.notified_at) <= :due and rr.notified_at > :since
            returning rr.request_id, rr.shop_id as target_id, rr.reminder_count as attempt""", params);
      shops.forEach((due, shopIds) ->
            safely(() -> requestNotifier.remind(due.requestId(), shopIds, due.attempt()), due.requestId()));

      Map<Due, List<Long>> masters = due("""
            update service_recipients sr set reminded_at = :now, reminder_count = sr.reminder_count + 1
            from service_requests r
            where r.id = sr.request_id and r.status = 'ACTIVE' and r.expires_at > :now
              and sr.status in ('DELIVERED', 'SEEN') and sr.replied_at is null and sr.reminder_count < :max
              and coalesce(sr.reminded_at, sr.notified_at) <= :due and sr.notified_at > :since
            returning sr.request_id, sr.master_id as target_id, sr.reminder_count as attempt""", params);
      masters.forEach((due, masterIds) ->
            safely(() -> serviceNotifier.remind(due.requestId(), masterIds, due.attempt()), due.requestId()));

      if (!shops.isEmpty() || !masters.isEmpty()) {
         log.info("Напоминания «Ответьте»: запросов {}, заявок мастерам {}", shops.size(), masters.size());
      }
   }

   /** Запрос и какое по счёту напоминание (1…3) — от этого зависит текст. */
   record Due(Long requestId, int attempt) {
   }

   private Map<Due, List<Long>> due(String sql, MapSqlParameterSource params) {
      Map<Due, List<Long>> byRequest = new LinkedHashMap<>();
      jdbc.query(sql, params, rs -> {
         byRequest.computeIfAbsent(new Due(rs.getLong("request_id"), rs.getInt("attempt")), id -> new ArrayList<>())
               .add(rs.getLong("target_id"));
      });
      return byRequest;
   }

   private static void safely(Runnable send, Long requestId) {
      try {
         send.run();
      } catch (RuntimeException e) {
         log.warn("Напоминание по запросу {} не ушло: {}", requestId, e.getMessage());
      }
   }
}
