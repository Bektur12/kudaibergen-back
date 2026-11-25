package kg.kudaibergen.stats;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * «Отвечает за ~5 мин» (shops.reply_minutes): раз в час — медиана времени от рассылки до ответа «Есть / Нет» за
 * 30 дней. Медиана, а не среднее: один ответ утром на ночной запрос не портит продавцу картину. Меньше трёх
 * ответов — null, бейджа нет. Одним UPDATE по всем магазинам: их до 2 000, ответов за месяц — десятки тысяч.
 */
@Component
public class ShopReplySpeedJob {

   private static final Logger log = LoggerFactory.getLogger(ShopReplySpeedJob.class);
   static final Duration WINDOW = Duration.ofDays(30);
   static final int MIN_REPLIES = 3;

   private final NamedParameterJdbcTemplate jdbc;
   private final Clock clock;

   public ShopReplySpeedJob(NamedParameterJdbcTemplate jdbc, Clock clock) {
      this.jdbc = jdbc;
      this.clock = clock;
   }

   @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
   @Transactional
   public void recount() {
      int changed = jdbc.update("""
            update shops s set reply_minutes = x.minutes
            from (select sh.id, m.minutes
                  from shops sh
                  left join (select rr.shop_id,
                                    greatest(1, ceil(percentile_cont(0.5) within group (
                                        order by extract(epoch from rr.replied_at - rr.notified_at)) / 60))::int
                                        as minutes
                             from request_recipients rr
                             where rr.notified_at >= :since and rr.replied_at is not null
                             group by rr.shop_id
                             having count(*) >= :minReplies) m on m.shop_id = sh.id) x
            where s.id = x.id and s.reply_minutes is distinct from x.minutes""",
            Map.of("since", Timestamp.from(clock.instant().minus(WINDOW)), "minReplies", MIN_REPLIES));
      if (changed > 0) {
         log.info("Скорость ответа пересчитана у магазинов: {}", changed);
      }
   }
}
