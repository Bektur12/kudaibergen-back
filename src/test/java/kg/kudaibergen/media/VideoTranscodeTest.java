package kg.kudaibergen.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.media.storage.LocalMediaStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Пережатие настоящим ffmpeg (jar org.bytedeco для Mac / Linux): вертикальное 1080×1920 → 720×1280 H.264. */
class VideoTranscodeTest {

   private static VideoTranscoder ffmpeg;

   @TempDir
   Path dir;

   @BeforeAll
   static void findFfmpeg() {
      ffmpeg = VideoTranscoder.detect().orElse(null);
   }

   @Test
   void ключПережатогоВидео() {
      assertThat(VideoTranscodeJob.transcodedKey("part/video/87bfec9f-ba9b-487e-9091-39d901cf8b31.mov"))
            .isEqualTo("part/video/87bfec9f-ba9b-487e-9091-39d901cf8b31-720.mp4");
      assertThat(VideoTranscodeJob.transcodedKey("service/video/a28775a3-f798-49f7-8b82-704c6d51700b.MP4"))
            .isEqualTo("service/video/a28775a3-f798-49f7-8b82-704c6d51700b-720.mp4");
   }

   @Test
   void вертикальноеВидеоСтановится720pH264СИндексомВНачале() throws Exception {
      assumeThat(ffmpeg).as("ffmpeg").isNotNull();
      Path source = sample(dir.resolve("phone.mov"));
      Path target = dir.resolve("out.mp4");

      ffmpeg.transcode(source, target);

      String info = probe(target);
      assertThat(info).contains("Video: h264").contains("yuv420p").contains("Audio: aac");
      Matcher size = Pattern.compile(", (\\d{2,5})x(\\d{2,5})").matcher(info);
      assertThat(size.find()).isTrue();
      assertThat(size.group(1)).isEqualTo("720");
      assertThat(size.group(2)).isEqualTo("1280");
      assertThat(Files.size(target)).isLessThan(Files.size(source));
      assertThat(topLevelBoxes(target).indexOf("moov")).isLessThan(topLevelBoxes(target).indexOf("mdat"));
   }

   @Test
   void битыйФайл_ошибкаСВыводомFfmpeg() throws Exception {
      assumeThat(ffmpeg).as("ffmpeg").isNotNull();
      Path broken = Files.writeString(dir.resolve("broken.mov"), "не видео");
      Path target = dir.resolve("out.mp4");
      try {
         ffmpeg.transcode(broken, target);
         throw new AssertionError("ожидалась ошибка");
      } catch (IOException e) {
         assertThat(e.getMessage()).startsWith("ffmpeg завершился с кодом");
      }
   }

   @Test
   void задачаКладётНовыйФайлИПереводитНаНегоВидеоИСообщения() throws Exception {
      assumeThat(ffmpeg).as("ffmpeg").isNotNull();
      Path root = dir.resolve("uploads");
      String oldKey = "part/video/87bfec9f-ba9b-487e-9091-39d901cf8b31.mov";
      Files.createDirectories(root.resolve("part/video"));
      sample(root.resolve(oldKey));
      LocalMediaStorage storage = new LocalMediaStorage(new AppProperties(null, null, null, null, null, null, null,
            new AppProperties.Media("local", root.toString(), null, DataSize.ofMegabytes(10), DataSize.ofMegabytes(15),
                  DataSize.ofMegabytes(100), 60)));
      MediaRepository repository = mock(MediaRepository.class);
      Media video = Media.video(5L, MediaPurpose.PART, oldKey, "video/quicktime", 2, Files.size(root.resolve(oldKey)),
            null, null, null, null);
      ReflectionTestUtils.setField(video, "id", 42L);
      when(repository.nextToTranscode()).thenReturn(Optional.of(video), Optional.empty());
      when(repository.replaceVideoFile(eq(42L), eq(oldKey), any(), anyLong())).thenReturn(1);

      int done = new VideoTranscodeJob(repository, storage, transactions(), ffmpeg).run();

      String newKey = "part/video/87bfec9f-ba9b-487e-9091-39d901cf8b31-720.mp4";
      assertThat(done).isEqualTo(1);
      assertThat(root.resolve(newKey)).exists();
      assertThat(root.resolve(oldKey)).exists();
      verify(repository).replaceVideoFile(42L, oldKey, newKey, Files.size(root.resolve(newKey)));
      verify(repository).repointMessages(oldKey, newKey);
      verify(repository, never()).countTranscodeFailure(any(), eq(VideoTranscodeJob.MAX_ATTEMPTS));
   }

   @Test
   void сбойСчитаетсяПопыткойИОстанавливаетПроход() {
      assumeThat(ffmpeg).as("ffmpeg").isNotNull();
      LocalMediaStorage storage = new LocalMediaStorage(new AppProperties(null, null, null, null, null, null, null,
            new AppProperties.Media("local", dir.toString(), null, DataSize.ofMegabytes(10), DataSize.ofMegabytes(15),
                  DataSize.ofMegabytes(100), 60)));
      MediaRepository repository = mock(MediaRepository.class);
      Media video = Media.video(5L, MediaPurpose.PART, "part/video/нет-файла.mov", "video/quicktime", 2, 10,
            null, null, null, null);
      ReflectionTestUtils.setField(video, "id", 7L);
      when(repository.nextToTranscode()).thenReturn(Optional.of(video));

      int done = new VideoTranscodeJob(repository, storage, transactions(), ffmpeg).run();

      assertThat(done).isZero();
      verify(repository).countTranscodeFailure(7L, VideoTranscodeJob.MAX_ATTEMPTS);
      verify(repository, never()).replaceVideoFile(any(), any(), any(), anyLong());
   }

   private static PlatformTransactionManager transactions() {
      PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
      when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
      return manager;
   }

   /** Как с телефона: вертикальное 1080×1920, 60 кадров/с, высокий битрейт, со звуком, индекс в конце. */
   private static Path sample(Path file) throws Exception {
      run(List.of(ffmpeg.binary(), "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "testsrc2=size=1080x1920:rate=60:duration=2",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
            "-c:v", "libx264", "-preset", "ultrafast", "-b:v", "20M", "-c:a", "aac", file.toString()));
      return file;
   }

   private static String probe(Path file) throws Exception {
      return run(List.of(ffmpeg.binary(), "-hide_banner", "-i", file.toString()));
   }

   private static String run(List<String> command) throws Exception {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      process.waitFor(60, TimeUnit.SECONDS);
      return output;
   }

   private static List<String> topLevelBoxes(Path file) throws IOException {
      byte[] bytes = Files.readAllBytes(file);
      List<String> boxes = new ArrayList<>();
      int offset = 0;
      while (offset + 8 <= bytes.length) {
         long size = ((bytes[offset] & 0xFFL) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
               | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
         boxes.add(new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII));
         if (size < 8) {
            break;
         }
         offset += (int) size;
      }
      return boxes;
   }
}
