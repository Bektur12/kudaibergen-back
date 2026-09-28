package kg.kudaibergen.chat.media;

import kg.kudaibergen.chat.entity.MessageType;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Вложения чата: фото, голосовые, видео. В базе лежит ключ, клиенту при каждой выдаче собирается URL
 * ({@link #urlFor}). Реализации: локальная папка (dev, app.media.storage=local) и бакет MinIO/S3 (s3).
 * Фото сжимает и чистит от EXIF и геометки телефон (ТЗ 9.2, 14).
 */
public abstract class ChatMediaStorage {

   protected final AppProperties.Media config;

   protected ChatMediaStorage(AppProperties properties) {
      this.config = properties.media();
   }

   public record Stored(String key, String mimeType) {
   }

   /** URL для клиента по ключу из базы; null — вложения нет. */
   public abstract String urlFor(String key);

   /** Кладёт уже проверенный файл и возвращает ключ. */
   protected abstract String save(MultipartFile file, MessageType type, String extension);

   public Stored store(MultipartFile file, MessageType type) {
      if (file == null || file.isEmpty()) {
         throw new BadRequestException("FILE_REQUIRED", "Файл не передан");
      }
      String mimeType = file.getContentType();
      DataSize limit = switch (type) {
         case PHOTO -> requirePrefix(mimeType, "image/", "Ожидалось изображение", config.maxPhotoSize());
         case VOICE -> requirePrefix(mimeType, "audio/", "Ожидалось аудио", config.maxVoiceSize());
         case VIDEO -> requirePrefix(mimeType, "video/", "Ожидалось видео", config.maxVideoSize());
         default -> throw new BadRequestException("BAD_MEDIA_TYPE", "Тип вложения: PHOTO, VOICE или VIDEO");
      };
      if (file.getSize() > limit.toBytes()) {
         throw new BadRequestException("FILE_TOO_LARGE", "Файл больше допустимого размера (" + limit + ")");
      }
      return new Stored(save(file, type, extensionOf(file.getOriginalFilename())), mimeType);
   }

   private static DataSize requirePrefix(String mimeType, String prefix, String message, DataSize limit) {
      if (mimeType == null || !mimeType.startsWith(prefix)) {
         throw new BadRequestException("BAD_MEDIA_TYPE", message);
      }
      return limit;
   }

   /** Только короткое буквенно-цифровое расширение: имя файла от клиента в ключ не попадает. */
   static String extensionOf(String originalFilename) {
      if (!StringUtils.hasText(originalFilename)) {
         return "";
      }
      int dot = originalFilename.lastIndexOf('.');
      String extension = dot >= 0 ? originalFilename.substring(dot + 1) : "";
      return extension.matches("[A-Za-z0-9]{1,5}") ? "." + extension.toLowerCase() : "";
   }
}
