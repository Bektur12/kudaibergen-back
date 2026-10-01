package kg.kudaibergen.admin.broadcasts;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.admin.broadcasts.BroadcastAudience.AudienceQuery;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.Audience;
import kg.kudaibergen.notification.push.PushMessage;
import kg.kudaibergen.notification.push.UserPushes;
import kg.kudaibergen.user.entity.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Фоновая отправка рассылок: раз в 20 секунд запланированные к этому времени получают список получателей
 * (аудитория на момент старта), затем пуши уходят батчами по {@link #BATCH}, по языку получателя, с данными
 * {type: BROADCAST, broadcastId}. В тихие часы 22:00–07:00 отправка не идёт и продолжится в 07:00.
 * Отменённая на середине — остальным не уходит.
 */
@Component
public class BroadcastSender {

   static final int BATCH = 500;
   static final int BATCHES_PER_TICK = 20;
   private static final Logger log = LoggerFactory.getLogger(BroadcastSender.class);

   private final NamedParameterJdbcTemplate jdbc;
   private final BroadcastAudience audiences;
   private final BroadcastService broadcasts;
   private final UserPushes pushes;
   private final TransactionTemplate transaction;
   private final Clock clock;

   public BroadcastSender(NamedParameterJdbcTemplate jdbc, BroadcastAudience audiences, BroadcastService broadcasts,
                          UserPushes pushes, PlatformTransactionManager transactions, Clock clock) {
      this.jdbc = jdbc;
      this.audiences = audiences;
      this.broadcasts = broadcasts;
      this.pushes = pushes;
      this.transaction = new TransactionTemplate(transactions);
      this.clock = clock;
   }

   @Scheduled(fixedDelayString = "PT20S", initialDelayString = "PT15S")
   public void run() {
      if (BroadcastService.quiet(clock.instant())) {
         return;
      }
      start();
      for (Long id : jdbc.queryForList("select id from broadcasts where status = 'SENDING' order by id",
            Map.of(), Long.class)) {
         for (int i = 0; i < BATCHES_PER_TICK; i++) {
            if (!sendBatch(id)) {
               break;
            }
         }
      }
   }

   /** Запланированные к этому времени: получатели фиксируются, статус SENDING. */
   void start() {
      List<Long> due = jdbc.queryForList("""
            select id from broadcasts where status = 'SCHEDULED' and scheduled_at <= now() order by scheduled_at""",
            Map.of(), Long.class);
      for (Long id : due) {
         transaction.executeWithoutResult(status -> {
            Map<String, Object> row = jdbc.queryForMap("""
                  select audience, filters::text as filters from broadcasts where id = :id and status = 'SCHEDULED'
                  for update skip locked""", Map.of("id", id));
            AudienceQuery query = audiences.of(Audience.valueOf((String) row.get("audience")),
                  broadcasts.filters((String) row.get("filters")));
            MapSqlParameterSource params = query.params().addValue("id", id);
            int recipients = jdbc.update("insert into broadcast_recipients (broadcast_id, user_id) select :id, a.id from ("
                  + query.sql() + ") a on conflict do nothing", params);
            jdbc.update("""
                  update broadcasts set status = 'SENDING', started_at = now(), recipients_count = :n, updated_at = now()
                  where id = :id""", Map.of("id", id, "n", recipients));
            log.info("Рассылка {}: начата, получателей {}", id, recipients);
         });
      }
   }

   /** Следующие {@link #BATCH} получателей; false — больше никого, рассылка завершена (или отменена). */
   boolean sendBatch(Long id) {
      Map<String, Object> b = jdbc.queryForMap("""
            select status, title_ru, title_kg, body_ru, body_kg, progress_user_id from broadcasts where id = :id""",
            Map.of("id", id));
      if (!"SENDING".equals(b.get("status"))) {
         return false;
      }
      long progress = ((Number) b.get("progress_user_id")).longValue();
      List<Long> batch = jdbc.queryForList("""
            select user_id from broadcast_recipients where broadcast_id = :id and user_id > :after
            order by user_id limit :limit""", Map.of("id", id, "after", progress, "limit", BATCH), Long.class);
      if (batch.isEmpty()) {
         jdbc.update("update broadcasts set status = 'SENT', sent_at = now(), updated_at = now() where id = :id",
               Map.of("id", id));
         log.info("Рассылка {}: отправлена", id);
         return false;
      }
      String titleRu = (String) b.get("title_ru");
      String bodyRu = (String) b.get("body_ru");
      String titleKg = b.get("title_kg") == null ? titleRu : (String) b.get("title_kg");
      String bodyKg = b.get("body_kg") == null ? bodyRu : (String) b.get("body_kg");
      Map<String, String> data = new HashMap<>(Map.of("type", "BROADCAST", "broadcastId", id.toString()));
      pushes.send(batch, (user, settings) -> user.getLang() == Lang.KG
            ? new PushMessage(titleKg, bodyKg, data) : new PushMessage(titleRu, bodyRu, data));
      long last = batch.get(batch.size() - 1);
      transaction.executeWithoutResult(status -> {
         int delivered = jdbc.update("""
               update broadcast_recipients r set delivered_at = now()
               where r.broadcast_id = :id and r.user_id in (:users)
                 and exists (select 1 from device_tokens d where d.user_id = r.user_id)""",
               new MapSqlParameterSource("id", id).addValue("users", batch));
         jdbc.update("""
               update broadcasts set delivered_count = delivered_count + :delivered, progress_user_id = :last,
                      updated_at = now() where id = :id""", Map.of("id", id, "delivered", delivered, "last", last));
      });
      return true;
   }
}
