package kg.kudaibergen.media;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

/** React Native присылает файлы как application/octet-stream или без типа — тип определяется по содержимому. */
class MediaTypesTest {

   private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
   private static final byte[] MP4 = "\0\0\0\u0018ftypisom\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1);
   private static final byte[] MOV = "\0\0\0\u0014ftypqt  \0\0\0\0".getBytes(StandardCharsets.ISO_8859_1);
   private static final byte[] M4A = "\0\0\0\u0018ftypM4A \0\0\0\0".getBytes(StandardCharsets.ISO_8859_1);

   @Test
   void присланныйТипОставляетсяЕслиПодходит() {
      assertThat(MediaTypes.resolve(new MockMultipartFile("f", "a.jpg", "image/png", JPEG), "image/")).isEqualTo("image/png");
   }

   @Test
   void безТипаОпределяетсяПоСодержимому() {
      assertThat(MediaTypes.resolve(new MockMultipartFile("f", "photo", "application/octet-stream", JPEG), "image/"))
            .isEqualTo("image/jpeg");
      assertThat(MediaTypes.resolve(new MockMultipartFile("f", "clip", null, MP4), "video/")).isEqualTo("video/mp4");
      assertThat(MediaTypes.resolve(new MockMultipartFile("f", "clip", "", MOV), "video/")).isEqualTo("video/quicktime");
      assertThat(MediaTypes.resolve(new MockMultipartFile("f", "voice", "application/octet-stream", M4A), "audio/"))
            .isEqualTo("audio/mp4");
   }

   @Test
   void иначеПоРасширению() {
      byte[] unknown = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
      assertThat(MediaTypes.resolve(new MockMultipartFile("f", "IMG_1.HEIC", "application/octet-stream", unknown),
            "image/")).isEqualTo("image/heic");
      assertThat(MediaTypes.resolve(new MockMultipartFile("f", "rec.3gp", null, unknown), "audio/"))
            .isEqualTo("audio/3gpp");
      assertThat(MediaTypes.resolve(new MockMultipartFile("f", "doc.jpg", "application/pdf", unknown), "image/"))
            .as("явный другой тип не переопределяется").isEqualTo("application/pdf");
   }
}
