package kg.kudaibergen.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.media.storage.MediaStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Загрузка фото: проверка размера и формата, пережатие в 1080 и 320 px, запись в хранилище.
 * Фото, так и не прикреплённые к товару, пока остаются в хранилище (чистка — вместе с админкой).
 */
@Service
public class MediaService {

   private static final String JPEG = "image/jpeg";

   private final MediaRepository media;
   private final MediaStorage storage;
   private final DataSize maxPhotoSize;

   public MediaService(MediaRepository media, MediaStorage storage, AppProperties properties) {
      this.media = media;
      this.storage = storage;
      this.maxPhotoSize = properties.media().maxPhotoSize();
   }

   /** Пережатие идёт до транзакции: пока файл обрабатывается, соединение с базой не занято. */
   public PhotoDto uploadPhoto(Long ownerId, MediaPurpose purpose, MultipartFile file) {
      if (file == null || file.isEmpty()) {
         throw new BadRequestException("FILE_REQUIRED", "Файл не передан");
      }
      if (file.getSize() > maxPhotoSize.toBytes()) {
         throw new BadRequestException("FILE_TOO_LARGE", "Фото больше " + maxPhotoSize.toMegabytes() + " МБ");
      }
      byte[] bytes;
      try {
         bytes = file.getBytes();
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось прочитать файл", e);
      }
      ImageProcessor.Result result = ImageProcessor.process(bytes);
      String base = purpose.name().toLowerCase() + "/" + UUID.randomUUID();
      String large = base + "-1080.jpg";
      String thumb = base + "-320.jpg";
      storage.put(large, new ByteArrayInputStream(result.large()), result.large().length, JPEG);
      storage.put(thumb, new ByteArrayInputStream(result.thumb()), result.thumb().length, JPEG);
      Media saved = media.save(new Media(ownerId, purpose, large, thumb, result.width(), result.height(),
            result.large().length));
      return dto(saved);
   }

   @Transactional(readOnly = true)
   public List<Media> findAll(Collection<Long> ids) {
      return media.findAllById(ids);
   }

   /** Фото по id в порядке запроса; неизвестные id пропускаются. */
   @Transactional(readOnly = true)
   public Map<Long, PhotoDto> photos(Collection<Long> ids) {
      Map<Long, PhotoDto> byId = new LinkedHashMap<>();
      if (ids.isEmpty()) {
         return byId;
      }
      Map<Long, Media> found = new LinkedHashMap<>();
      media.findAllById(ids).forEach(item -> found.put(item.getId(), item));
      ids.forEach(id -> {
         Media item = found.get(id);
         if (item != null) {
            byId.put(id, dto(item));
         }
      });
      return byId;
   }

   /** Превью 320 px — для аватаров; null — фото нет. */
   @Transactional(readOnly = true)
   public String thumbUrl(Long mediaId) {
      return mediaId == null ? null : media.findById(mediaId).map(item -> storage.urlFor(item.getKey320())).orElse(null);
   }

   /** Превью 320 px пачкой: id фото → URL. */
   @Transactional(readOnly = true)
   public Map<Long, String> thumbUrls(Collection<Long> mediaIds) {
      Map<Long, String> urls = new LinkedHashMap<>();
      photos(mediaIds.stream().filter(Objects::nonNull).distinct().toList())
            .forEach((id, photo) -> urls.put(id, photo.thumbUrl()));
      return urls;
   }

   /**
    * Фото можно прикрепить: оно есть, загружено для одной из целей purposes и одним из пользователей
    * owners. Иначе 400 BAD_PHOTO — клиент загружает фото заново.
    */
   @Transactional(readOnly = true)
   public void requireUsable(Collection<Long> mediaIds, Collection<Long> owners, Set<MediaPurpose> purposes) {
      List<Media> found = media.findAllById(mediaIds);
      boolean ok = found.size() == new HashSet<>(mediaIds).size() && found.stream()
            .allMatch(item -> purposes.contains(item.getPurpose()) && owners.contains(item.getOwnerId()));
      if (!ok) {
         throw new BadRequestException("BAD_PHOTO", "Фото не найдено — загрузите его заново");
      }
   }

   public PhotoDto dto(Media item) {
      return new PhotoDto(item.getId(), storage.urlFor(item.getKey1080()), storage.urlFor(item.getKey320()),
            item.getWidth(), item.getHeight());
   }
}
