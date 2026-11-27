package kg.kudaibergen.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import kg.kudaibergen.media.storage.MediaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Пережимает видео (новые и уже загруженные) в лёгкий MP4 — см. {@link VideoTranscoder}. Сначала новые: их
 * смотрят в ленте прямо сейчас. Пережатый файл кладётся под новым ключом «…-720.mp4», строка media переводится
 * на него, исходник остаётся в original_key. Получилось тяжелее исходного MP4 — оставляем исходник.
 * <p>
 * Работает в своём потоке, по одному видео: ffmpeg занимает процессор на десятки секунд, а общий поток
 * {@code @Scheduled} нужен остальным задачам. Сбой — до {@link #MAX_ATTEMPTS} попыток, потом FAILED и видео
 * остаётся как загружено.
 */
@Component
public class VideoTranscodeJob {

   static final int MAX_ATTEMPTS = 3;
   /** За один проход, чтобы поток не занимал процессор часами подряд. */
   private static final int MAX_PER_RUN = 50;
   private static final Logger log = LoggerFactory.getLogger(VideoTranscodeJob.class);

   private final MediaRepository media;
   private final MediaStorage storage;
   private final TransactionTemplate tx;
   private final boolean enabled;
   private final AtomicBoolean running = new AtomicBoolean();
   private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "video-transcode");
      thread.setDaemon(true);
      thread.setPriority(Thread.MIN_PRIORITY);
      return thread;
   });
   private volatile Optional<VideoTranscoder> transcoder;

   @Autowired
   public VideoTranscodeJob(MediaRepository media, MediaStorage storage, PlatformTransactionManager transactions,
                            @Value("${app.media.transcode.enabled:true}") boolean enabled) {
      this.media = media;
      this.storage = storage;
      this.tx = new TransactionTemplate(transactions);
      this.enabled = enabled;
   }

   /** Для тестов: свой ffmpeg. */
   VideoTranscodeJob(MediaRepository media, MediaStorage storage, PlatformTransactionManager transactions,
                     VideoTranscoder transcoder) {
      this(media, storage, transactions, true);
      this.transcoder = Optional.of(transcoder);
   }

   @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT30S")
   public void tick() {
      if (enabled && running.compareAndSet(false, true)) {
         worker.execute(() -> {
            try {
               run();
            } catch (RuntimeException e) {
               log.warn("Пережатие видео остановлено: {}", e.getMessage());
            } finally {
               running.set(false);
            }
         });
      }
   }

   @PreDestroy
   void stop() {
      worker.shutdownNow();
   }

   /** Пережимает ждущие видео; возвращает число обработанных. Сбой — выходим, следующая попытка через 30 с. */
   int run() {
      Optional<VideoTranscoder> ffmpeg = transcoder();
      if (ffmpeg.isEmpty()) {
         return 0;
      }
      int done = 0;
      while (done < MAX_PER_RUN && !Thread.currentThread().isInterrupted()) {
         Optional<Media> next = tx.execute(status -> media.nextToTranscode());
         if (next == null || next.isEmpty() || !process(next.get(), ffmpeg.get())) {
            break;
         }
         done++;
      }
      return done;
   }

   private Optional<VideoTranscoder> transcoder() {
      Optional<VideoTranscoder> found = transcoder;
      if (found == null) {
         found = VideoTranscoder.detect();
         transcoder = found;
         if (found.isPresent()) {
            log.info("Пережатие видео включено, ffmpeg: {}", found.get().binary());
         } else {
            log.warn("ffmpeg не найден — видео не пережимаются (на Mac: brew install ffmpeg)");
         }
      }
      return found;
   }

   /** true — видео обработано (пережато или оставлено), false — сбой. */
   boolean process(Media video, VideoTranscoder ffmpeg) {
      String oldKey = video.getFileKey();
      Path source = null;
      Path target = null;
      try {
         source = Files.createTempFile("video-src-", extension(oldKey));
         target = Files.createTempFile("video-720-", ".mp4");
         try (InputStream content = storage.get(oldKey)) {
            Files.copy(content, source, StandardCopyOption.REPLACE_EXISTING);
         }
         long started = System.nanoTime();
         ffmpeg.transcode(source, target);
         long originalSize = Files.size(source);
         long size = Files.size(target);
         long seconds = (System.nanoTime() - started) / 1_000_000_000L;
         if (size >= originalSize && "video/mp4".equals(video.getMimeType())) {
            tx.executeWithoutResult(status -> media.markTranscoded(video.getId()));
            log.info("Видео {} и так лёгкое ({} КБ) — оставлено как есть", video.getId(), originalSize / 1024);
            return true;
         }
         String newKey = transcodedKey(oldKey);
         try (InputStream content = Files.newInputStream(target)) {
            storage.put(newKey, content, size, "video/mp4");
         }
         Integer replaced = tx.execute(status -> {
            int rows = media.replaceVideoFile(video.getId(), oldKey, newKey, size);
            if (rows > 0) {
               media.repointMessages(oldKey, newKey);
            }
            return rows;
         });
         if (replaced == null || replaced == 0) {
            // видео успели удалить или заменить, пока пережимали
            storage.delete(newKey);
            return true;
         }
         log.info("Видео {} пережато за {} с: {} → {} КБ", video.getId(), seconds, originalSize / 1024, size / 1024);
         return true;
      } catch (IOException | RuntimeException e) {
         log.warn("Видео {} не пережато: {}", video.getId(), e.getMessage());
         tx.executeWithoutResult(status -> media.countTranscodeFailure(video.getId(), MAX_ATTEMPTS));
         return false;
      } finally {
         deleteQuietly(source);
         deleteQuietly(target);
      }
   }

   /** «part/video/<uuid>.mov» → «part/video/<uuid>-720.mp4» (CDN пропускает такие ключи). */
   static String transcodedKey(String key) {
      return key.replaceFirst("\\.[A-Za-z0-9]{1,5}$", "") + "-720.mp4";
   }

   private static String extension(String key) {
      int dot = key.lastIndexOf('.');
      String extension = dot >= 0 ? key.substring(dot) : "";
      return extension.matches("\\.[A-Za-z0-9]{1,5}") ? extension : ".bin";
   }

   private static void deleteQuietly(Path path) {
      if (path != null) {
         try {
            Files.deleteIfExists(path);
         } catch (IOException e) {
            log.debug("Не удалён временный файл {}", path);
         }
      }
   }
}
