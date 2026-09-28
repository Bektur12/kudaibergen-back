package kg.kudaibergen.chat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import kg.kudaibergen.chat.entity.MessageType;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.media.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatAttachmentsTest {

   private final List<String> saved = new ArrayList<>();

   private final ChatAttachments storage = new ChatAttachments(new MediaStorage() {

      @Override
      public String urlFor(String key) {
         return key;
      }

      @Override
      public void put(String key, InputStream content, long size, String contentType) {
         saved.add(key);
      }
   }, new AppProperties(null, null, null, null, null, null, null, new AppProperties.Media("local", "uploads", null,
         DataSize.ofBytes(10), DataSize.ofMegabytes(15), DataSize.ofMegabytes(100), 60)));

   @Test
   void расширениеТолькоКороткоеИБезПутей() {
      assertThat(ChatAttachments.extensionOf("photo.JPG")).isEqualTo(".jpg");
      assertThat(ChatAttachments.extensionOf("voice.m4a")).isEqualTo(".m4a");
      assertThat(ChatAttachments.extensionOf("../../etc/passwd")).isEmpty();
      assertThat(ChatAttachments.extensionOf("a.tar.gz/../x")).isEmpty();
      assertThat(ChatAttachments.extensionOf(null)).isEmpty();
   }

   @Test
   void типФайлаИРазмерПроверяются() {
      MockMultipartFile photo = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[5]);
      assertThat(storage.store(photo, MessageType.PHOTO).key()).startsWith("chat/photo/").endsWith(".jpg");
      assertThat(saved).hasSize(1);

      MockMultipartFile big = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[11]);
      assertThatThrownBy(() -> storage.store(big, MessageType.PHOTO))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("больше");

      MockMultipartFile notAudio = new MockMultipartFile("file", "v.m4a", "image/png", new byte[5]);
      assertThatThrownBy(() -> storage.store(notAudio, MessageType.VOICE))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("аудио");

      assertThatThrownBy(() -> storage.store(photo, MessageType.TEXT)).isInstanceOf(BadRequestException.class);
   }

}
