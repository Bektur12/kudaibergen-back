package kg.kudaibergen.chat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/** Сохраняет медиа-вложения чата на диск (dev). В проде заменяется на S3/GCS без изменения ChatService. */
@Service
public class ChatMediaStorage {

   private final AppProperties properties;

   public ChatMediaStorage(AppProperties properties) {
      this.properties = properties;
   }

   public record Stored(String url, String mimeType) {
   }

   public Stored store(MultipartFile file, String type) {
      if (file == null || file.isEmpty()) {
         throw new BadRequestException("FILE_REQUIRED", "Файл не передан", "file");
      }
      String mimeType = file.getContentType();
      DataSize limit = switch (type) {
         case "PHOTO" -> requirePrefix(mimeType, "image/", "Ожидалось изображение").maxPhotoSize();
         case "VOICE" -> requirePrefix(mimeType, "audio/", "Ожидалось аудио").maxVoiceSize();
         case "VIDEO" -> requirePrefix(mimeType, "video/", "Ожидалось видео").maxVideoSize();
         default -> throw new BadRequestException("BAD_MEDIA_TYPE", "Тип: PHOTO, VOICE или VIDEO", "type");
      };
      if (file.getSize() > limit.toBytes()) {
         throw new BadRequestException("FILE_TOO_LARGE",
               "Файл больше допустимого размера (%s)".formatted(limit), "file");
      }

      Path uploadDir = Path.of(properties.media().uploadDir()).toAbsolutePath().normalize();
      try {
         Files.createDirectories(uploadDir);
         String filename = UUID.randomUUID() + extensionOf(file.getOriginalFilename());
         Path target = uploadDir.resolve(filename);
         file.transferTo(target);
         return new Stored("/media/" + filename, mimeType);
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось сохранить файл", e);
      }
   }

   private AppProperties.Media requirePrefix(String mimeType, String prefix, String message) {
      if (mimeType == null || !mimeType.startsWith(prefix)) {
         throw new BadRequestException("BAD_MEDIA_TYPE", message, "file");
      }
      return properties.media();
   }

   private String extensionOf(String originalFilename) {
      if (!StringUtils.hasText(originalFilename)) {
         return "";
      }
      int dot = originalFilename.lastIndexOf('.');
      return dot >= 0 ? originalFilename.substring(dot) : "";
   }
}
