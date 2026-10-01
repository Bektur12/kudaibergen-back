package kg.kudaibergen.media;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import kg.kudaibergen.media.storage.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Чистка фото, загруженных, но так и не прикреплённых (ушли с формы, удалили товар, сменили аватар).
 * Сутки ждём — продавец может дозаполнять черновик. Сначала удаляются файлы (оба размера), потом строка:
 * если хранилище недоступно, строка остаётся и попадёт в следующий проход.
 */
@Component
public class MediaCleanupJob {

   static final Duration GRACE = Duration.ofHours(24);
   private static final int BATCH = 500;
   private static final int MAX_BATCHES = 20;
   private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

   private final MediaRepository media;
   private final MediaStorage storage;
   private final Clock clock;

   public MediaCleanupJob(MediaRepository media, MediaStorage storage, Clock clock) {
      this.media = media;
      this.storage = storage;
      this.clock = clock;
   }

   @Scheduled(cron = "0 45 3 * * *", zone = "Asia/Bishkek")
   public void run() {
      int removed = cleanup();
      if (removed > 0) {
         log.info("Удалено неприкреплённых фото и видео: {}", removed);
      }
   }

   /** Возвращает число удалённых фото. Каждая строка удаляется отдельно — один сбой не мешает остальным. */
   public int cleanup() {
      int removed = 0;
      for (int batch = 0; batch < MAX_BATCHES; batch++) {
         List<Media> orphans = media.findOrphans(clock.instant().minus(GRACE), BATCH);
         int removedInBatch = 0;
         for (Media item : orphans) {
            try {
               // у видео без обложки ключей фото нет
               for (String key : new String[]{item.getKey1080(), item.getKey320(), item.getVideoKey()}) {
                  if (key != null) {
                     storage.delete(key);
                  }
               }
               media.deleteById(item.getId());
               removedInBatch++;
            } catch (RuntimeException ex) {
               log.warn("Фото {} не удалено, повторим в следующий раз: {}", item.getId(), ex.getMessage());
            }
         }
         removed += removedInBatch;
         if (orphans.size() < BATCH || removedInBatch == 0) {
            break;
         }
      }
      return removed;
   }
}
