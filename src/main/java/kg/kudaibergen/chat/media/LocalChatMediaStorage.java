package kg.kudaibergen.chat.media;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import kg.kudaibergen.chat.entity.MessageType;
import kg.kudaibergen.common.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Dev: вложения в локальной папке, отдаются статикой по /media/** (см. MediaWebConfig). */
@Service
@ConditionalOnProperty(name = "app.media.storage", havingValue = "local", matchIfMissing = true)
public class LocalChatMediaStorage extends ChatMediaStorage {

   public LocalChatMediaStorage(AppProperties properties) {
      super(properties);
   }

   @Override
   public String urlFor(String key) {
      return key == null ? null : "/media/" + key;
   }

   @Override
   protected String save(MultipartFile file, MessageType type, String extension) {
      String key = "chat/" + type.name().toLowerCase() + "/" + UUID.randomUUID() + extension;
      Path target = Path.of(config.uploadDir()).toAbsolutePath().normalize().resolve(key);
      try {
         Files.createDirectories(target.getParent());
         file.transferTo(target);
         return key;
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось сохранить файл", e);
      }
   }
}
