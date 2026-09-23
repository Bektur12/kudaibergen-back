package kg.kudaibergen.common.idempotency;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Ключи живут сутки — дольше клиент повторять запрос не будет. */
@Component
public class IdempotencyCleanupJob {

   private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);
   private static final Duration TTL = Duration.ofDays(1);

   private final IdempotencyKeyRepository repository;

   public IdempotencyCleanupJob(IdempotencyKeyRepository repository) {
      this.repository = repository;
   }

   @Scheduled(cron = "0 15 3 * * *")
   @Transactional
   public void cleanup() {
      int removed = repository.deleteOlderThan(Instant.now().minus(TTL));
      if (removed > 0) {
         log.info("Удалено просроченных ключей идемпотентности: {}", removed);
      }
   }
}
