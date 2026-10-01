package kg.kudaibergen.media;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.media.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaVideoTest {

   private final MediaRepository repository = mock(MediaRepository.class);
   private final MediaStorage storage = mock(MediaStorage.class);
   private final MediaService service = new MediaService(repository, storage, new AppProperties(null, null, null, null,
         null, null, null, new AppProperties.Media("local", "uploads", null, DataSize.ofMegabytes(10),
         DataSize.ofMegabytes(15), DataSize.ofMegabytes(100), 60)));

   @Test
   void длительностьMp4ИзЗаголовка() throws IOException {
      // moov после большого mdat — как пишет большинство камер
      assertThat(Mp4Duration.read(new ByteArrayInputStream(mp4(0, 1000, 12_500, true)))).contains(12.5);
      assertThat(Mp4Duration.read(new ByteArrayInputStream(mp4(1, 600, 18_000, false)))).contains(30.0);
      assertThat(Mp4Duration.read(new ByteArrayInputStream("not a video at all".getBytes()))).isEmpty();
      assertThat(Mp4Duration.read(new ByteArrayInputStream(new byte[0]))).isEmpty();
   }

   @Test
   void длиннее30СекундНельзя() throws IOException {
      MockMultipartFile longMp4 = new MockMultipartFile("file", "a.mp4", "video/mp4", mp4(0, 1000, 31_000, true));

      assertThatThrownBy(() -> service.uploadVideo(1L, MediaPurpose.SERVICE, longMp4, 10, null))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("VIDEO_TOO_LONG");
   }

   @Test
   void длительностьИзMp4ВажнееПереданнойАДляДругихФорматовНужнаОтКлиента() throws IOException {
      assertThat(MediaService.videoSeconds(new MockMultipartFile("f", "a.mp4", "video/mp4",
            mp4(0, 1000, 12_400, true)), 29)).isEqualTo(12);
      MockMultipartFile webm = new MockMultipartFile("f", "a.webm", "video/webm", new byte[]{1, 2, 3});
      assertThat(MediaService.videoSeconds(webm, 20)).isEqualTo(20);
      assertThatThrownBy(() -> MediaService.videoSeconds(webm, null))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("DURATION_REQUIRED");
   }

   @Test
   void видеоСохраняетсяИОтдаётсяВложением() throws IOException {
      when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(storage.urlFor(any())).thenAnswer(invocation -> "https://cdn/" + invocation.getArgument(0));

      MediaItemDto item = service.uploadVideo(7L, MediaPurpose.SERVICE,
            new MockMultipartFile("file", "IMG_0001.MOV", "video/quicktime", mp4(0, 600, 6_000, true)), null, null);

      assertThat(item.kind()).isEqualTo(MediaKind.VIDEO);
      assertThat(item.durationSec()).isEqualTo(10);
      assertThat(item.url()).startsWith("https://cdn/service/video/").endsWith(".mov");
      assertThat(item.thumbUrl()).isNull();
      verify(storage).put(startsWith("service/video/"), any(), anyLong(), eq("video/quicktime"));
   }

   @Test
   void видеоТолькоДляЗаявкиНаУслугуИНеВместоФото() throws IOException {
      MockMultipartFile video = new MockMultipartFile("file", "a.mp4", "video/mp4", mp4(0, 1000, 5_000, true));
      assertThatThrownBy(() -> service.uploadVideo(1L, MediaPurpose.PART, video, null, null))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("VIDEO_NOT_ALLOWED");
      assertThatThrownBy(() -> service.uploadVideo(1L, MediaPurpose.SERVICE,
            new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1}), 5, null))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("BAD_MEDIA_TYPE");

      Media stored = Media.video(1L, MediaPurpose.SERVICE, "service/video/x.mp4", "video/mp4", 5, 100,
            null, null, null, null);
      org.springframework.test.util.ReflectionTestUtils.setField(stored, "id", 9L);
      when(repository.findAllById(List.of(9L))).thenReturn(List.of(stored));
      assertThatThrownBy(() -> service.requireUsable(List.of(9L), List.of(1L), Set.of(MediaPurpose.SERVICE)))
            .isInstanceOf(BadRequestException.class).extracting("code").isEqualTo("BAD_PHOTO");
      service.requireUsable(List.of(9L), List.of(1L), Set.of(MediaPurpose.SERVICE), true);
      assertThat(service.photos(List.of(9L))).isEmpty();
      assertThat(Optional.ofNullable(service.thumbUrl(null))).isEmpty();
   }

   /** Минимальный MP4: ftyp, mdat (1 КБ) и moov/mvhd; moovLast — moov в конце файла. */
   private static byte[] mp4(int version, int timescale, long duration, boolean moovLast) throws IOException {
      ByteArrayOutputStream mvhd = new ByteArrayOutputStream();
      DataOutputStream m = new DataOutputStream(mvhd);
      m.writeByte(version);
      m.write(new byte[3]);
      if (version == 1) {
         m.writeLong(0);
         m.writeLong(0);
         m.writeInt(timescale);
         m.writeLong(duration);
      } else {
         m.writeInt(0);
         m.writeInt(0);
         m.writeInt(timescale);
         m.writeInt((int) duration);
      }
      m.write(new byte[80]);
      byte[] moov = box("moov", concat(box("trak", new byte[16]), box("mvhd", mvhd.toByteArray())));
      byte[] ftyp = box("ftyp", "isom0000isommp41".getBytes(StandardCharsets.ISO_8859_1));
      byte[] mdat = box("mdat", new byte[1024]);
      return moovLast ? concat(ftyp, mdat, moov) : concat(ftyp, moov, mdat);
   }

   private static byte[] box(String type, byte[] payload) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DataOutputStream data = new DataOutputStream(out);
      data.writeInt(payload.length + 8);
      data.write(type.getBytes(StandardCharsets.ISO_8859_1));
      data.write(payload);
      return out.toByteArray();
   }

   private static byte[] concat(byte[]... parts) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      for (byte[] part : parts) {
         out.write(part);
      }
      return out.toByteArray();
   }
}
