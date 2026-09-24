package kg.kudaibergen.chat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import kg.kudaibergen.common.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Dev: медиа на локальном диске, отдаётся статикой по /media/**. */
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
   protected String save(MultipartFile file, String type, String extension) {
      Path uploadDir = Path.of(properties.media().uploadDir()).toAbsolutePath().normalize();
      try {
         Files.createDirectories(uploadDir);
         String filename = UUID.randomUUID() + extension;
         file.transferTo(uploadDir.resolve(filename));
         return filename;
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось сохранить файл", e);
      }
   }
}
