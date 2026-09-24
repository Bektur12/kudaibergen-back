package kg.kudaibergen.chat;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Хранилище медиа-вложений чата. В БД лежит только ссылка-ключ ({@link Stored#key()}),
 * клиенту она превращается в URL через {@link #urlFor(String)} при каждой выдаче сообщения.
 * Реализации: локальный диск (dev, app.media.storage=local) и S3-бакет (app.media.storage=s3).
 */
public abstract class ChatMediaStorage {

   protected final AppProperties properties;

   protected ChatMediaStorage(AppProperties properties) {
      this.properties = properties;
   }

   public record Stored(String key, String mimeType) {
   }

   /** Клиентский URL для ключа из БД; null-безопасно. */
   public abstract String urlFor(String key);

   /** Кладёт файл в хранилище и возвращает ключ. Файл уже провалидирован. */
   protected abstract String save(MultipartFile file, String type, String extension);

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
      return new Stored(save(file, type, extensionOf(file.getOriginalFilename())), mimeType);
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
