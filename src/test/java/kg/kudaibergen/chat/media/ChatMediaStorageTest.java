package kg.kudaibergen.chat.media;


import kg.kudaibergen.chat.entity.MessageType;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMediaStorageTest {

   private final ChatMediaStorage storage = new ChatMediaStorage(new AppProperties(null, null, null, null, null, null,
         null, new AppProperties.Media("local", "uploads", null, DataSize.ofBytes(10), DataSize.ofMegabytes(15),
         DataSize.ofMegabytes(100), 60))) {

      @Override
      public String urlFor(String key) {
         return key;
      }

      @Override
      protected String save(MultipartFile file, MessageType type, String extension) {
         return "chat/" + type.name().toLowerCase() + "/x" + extension;
      }
   };

   @Test
   void расширениеТолькоКороткоеИБезПутей() {
      assertThat(ChatMediaStorage.extensionOf("photo.JPG")).isEqualTo(".jpg");
      assertThat(ChatMediaStorage.extensionOf("voice.m4a")).isEqualTo(".m4a");
      assertThat(ChatMediaStorage.extensionOf("../../etc/passwd")).isEmpty();
      assertThat(ChatMediaStorage.extensionOf("a.tar.gz/../x")).isEmpty();
      assertThat(ChatMediaStorage.extensionOf(null)).isEmpty();
   }

   @Test
   void типФайлаИРазмерПроверяются() {
      MockMultipartFile photo = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[5]);
      assertThat(storage.store(photo, MessageType.PHOTO).key()).isEqualTo("chat/photo/x.jpg");

      MockMultipartFile big = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[11]);
      assertThatThrownBy(() -> storage.store(big, MessageType.PHOTO))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("больше");

      MockMultipartFile notAudio = new MockMultipartFile("file", "v.m4a", "image/png", new byte[5]);
      assertThatThrownBy(() -> storage.store(notAudio, MessageType.VOICE))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("аудио");

      assertThatThrownBy(() -> storage.store(photo, MessageType.TEXT)).isInstanceOf(BadRequestException.class);
   }

}
