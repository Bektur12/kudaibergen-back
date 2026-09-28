package kg.kudaibergen.chat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

import kg.kudaibergen.chat.entity.MessageType;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.media.storage.MediaStorage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Вложения чата — фото, голосовые, видео — кладутся в хранилище как есть. Фото сжимает и чистит
 * от EXIF и геометки телефон (ТЗ 9.2, 14). Проверяются тип и размер.
 */
@Component
public class ChatAttachments {

   private final MediaStorage storage;
   private final AppProperties.Media config;

   public ChatAttachments(MediaStorage storage, AppProperties properties) {
      this.storage = storage;
      this.config = properties.media();
   }

   public record Stored(String key, String mimeType) {
   }

   public String urlFor(String key) {
      return storage.urlFor(key);
   }

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
      String key = "chat/" + type.name().toLowerCase() + "/" + UUID.randomUUID()
            + extensionOf(file.getOriginalFilename());
      try (InputStream content = file.getInputStream()) {
         storage.put(key, content, file.getSize(), mimeType);
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось прочитать файл", e);
      }
      return new Stored(key, mimeType);
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
