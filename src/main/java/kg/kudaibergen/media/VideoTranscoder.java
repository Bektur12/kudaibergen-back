package kg.kudaibergen.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.bytedeco.javacpp.Loader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Пережатие видео для ленты: H.264 + AAC в MP4, короткая сторона не больше 720 px, до 30 кадров/с,
 * ~2,5 Мбит/с, индекс (moov) в начале файла — плеер начинает показ, не дожидаясь конца файла. HEVC с iPhone,
 * повёрнутые и вертикальные видео приводятся к виду, который играет любой телефон.
 * <p>
 * ffmpeg — из jar org.bytedeco:ffmpeg (сборка с libx264, Linux на сервере); нет его — ffmpeg из PATH.
 */
public class VideoTranscoder {

   static final Duration TIMEOUT = Duration.ofMinutes(5);
   /** Короткая сторона: 720 у горизонтального — высота, у вертикального — ширина; меньшее видео не растягиваем. */
   static final String SCALE = "scale=w='if(gt(iw,ih),-2,min(720,iw))':h='if(gt(iw,ih),min(720,ih),-2)'";
   private static final Logger log = LoggerFactory.getLogger(VideoTranscoder.class);

   private final String ffmpeg;

   VideoTranscoder(String ffmpeg) {
      this.ffmpeg = ffmpeg;
   }

   /** ffmpeg этой машины; не найден — пусто (видео остаются как загружены). */
   public static Optional<VideoTranscoder> detect() {
      try {
         String bundled = Loader.load(org.bytedeco.ffmpeg.ffmpeg.class);
         if (bundled != null && Files.isExecutable(Path.of(bundled))) {
            return Optional.of(new VideoTranscoder(bundled));
         }
      } catch (Throwable e) {
         log.debug("ffmpeg из jar недоступен: {}", e.toString());
      }
      try {
         Process probe = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
         probe.getInputStream().readAllBytes();
         if (probe.waitFor(10, TimeUnit.SECONDS) && probe.exitValue() == 0) {
            return Optional.of(new VideoTranscoder("ffmpeg"));
         }
      } catch (IOException e) {
         // нет в PATH
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      return Optional.empty();
   }

   String binary() {
      return ffmpeg;
   }

   List<String> command(Path source, Path target) {
      return List.of(ffmpeg, "-hide_banner", "-nostdin", "-y", "-loglevel", "error",
            "-i", source.toString(),
            "-map", "0:v:0", "-map", "0:a:0?",
            "-vf", SCALE + ",format=yuv420p",
            "-fpsmax", "30",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "26", "-maxrate", "2500k", "-bufsize", "5000k",
            "-profile:v", "high",
            "-c:a", "aac", "-b:a", "96k", "-ac", "2",
            "-map_metadata", "-1",
            "-movflags", "+faststart",
            "-threads", "2",
            target.toString());
   }

   /** Пережимает source в target (MP4). Ошибка ffmpeg или дольше {@link #TIMEOUT} — IOException с его выводом. */
   public void transcode(Path source, Path target) throws IOException {
      Path output = Files.createTempFile("ffmpeg-", ".log");
      try {
         Process process = new ProcessBuilder(command(source, target))
               .redirectErrorStream(true)
               .redirectOutput(output.toFile())
               .start();
         boolean finished;
         try {
            finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
         } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Пережатие прервано", e);
         }
         if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffmpeg не уложился в " + TIMEOUT.toMinutes() + " мин");
         }
         if (process.exitValue() != 0 || !Files.exists(target) || Files.size(target) == 0) {
            String text = Files.readString(output, StandardCharsets.UTF_8).strip();
            throw new IOException("ffmpeg завершился с кодом " + process.exitValue() + ": "
                  + (text.length() > 500 ? text.substring(text.length() - 500) : text));
         }
      } finally {
         Files.deleteIfExists(output);
      }
   }
}
