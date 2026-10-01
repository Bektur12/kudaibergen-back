package kg.kudaibergen.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
   /** Видео во вложениях заявки — до 30 секунд (спецификация 12.4); полсекунды — запас на округление камеры. */
   public static final int MAX_VIDEO_SECONDS = 30;
   /** Видео разрешено только во вложениях заявки на услугу. */
   static final Set<MediaPurpose> VIDEO_PURPOSES = Set.of(MediaPurpose.SERVICE);

   private final MediaRepository media;
   private final MediaStorage storage;
   private final DataSize maxPhotoSize;
   private final DataSize maxVideoSize;

   public MediaService(MediaRepository media, MediaStorage storage, AppProperties properties) {
      this.media = media;
      this.storage = storage;
      this.maxPhotoSize = properties.media().maxPhotoSize();
      this.maxVideoSize = properties.media().maxVideoSize();
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

   /**
    * Видео для вложений заявки: до {@link #MAX_VIDEO_SECONDS} с. Длительность MP4 / MOV сервер читает из файла сам,
    * для других форматов берёт durationSec от клиента. poster — необязательная обложка (кадр из видео на телефоне),
    * пережимается как фото.
    */
   public MediaItemDto uploadVideo(Long ownerId, MediaPurpose purpose, MultipartFile file, Integer durationSec,
                                   MultipartFile poster) {
      if (!VIDEO_PURPOSES.contains(purpose)) {
         throw new BadRequestException("VIDEO_NOT_ALLOWED", "Видео можно прикрепить только к заявке на услугу");
      }
      if (file == null || file.isEmpty()) {
         throw new BadRequestException("FILE_REQUIRED", "Файл не передан");
      }
      String mimeType = MediaTypes.resolve(file, "video/");
      if (mimeType == null || !mimeType.startsWith("video/")) {
         throw new BadRequestException("BAD_MEDIA_TYPE", "Ожидалось видео");
      }
      if (file.getSize() > maxVideoSize.toBytes()) {
         throw new BadRequestException("FILE_TOO_LARGE", "Видео больше " + maxVideoSize.toMegabytes() + " МБ");
      }
      int seconds = videoSeconds(file, durationSec);
      String base = purpose.name().toLowerCase() + "/video/" + UUID.randomUUID();
      String videoKey = base + extension(file.getOriginalFilename(), mimeType);
      try (InputStream content = file.getInputStream()) {
         storage.put(videoKey, content, file.getSize(), mimeType);
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось прочитать файл", e);
      }
      String posterLarge = null;
      String posterThumb = null;
      Integer width = null;
      Integer height = null;
      if (poster != null && !poster.isEmpty()) {
         if (poster.getSize() > maxPhotoSize.toBytes()) {
            throw new BadRequestException("FILE_TOO_LARGE", "Обложка больше " + maxPhotoSize.toMegabytes() + " МБ");
         }
         ImageProcessor.Result result;
         try {
            result = ImageProcessor.process(poster.getBytes());
         } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать обложку", e);
         }
         posterLarge = base + "-poster-1080.jpg";
         posterThumb = base + "-poster-320.jpg";
         storage.put(posterLarge, new ByteArrayInputStream(result.large()), result.large().length, JPEG);
         storage.put(posterThumb, new ByteArrayInputStream(result.thumb()), result.thumb().length, JPEG);
         width = result.width();
         height = result.height();
      }
      Media saved = media.save(Media.video(ownerId, purpose, videoKey, mimeType, seconds, file.getSize(),
            posterLarge, posterThumb, width, height));
      return item(saved);
   }

   /** Длительность: из MP4 / MOV, иначе от клиента. Больше 30 с — 400 VIDEO_TOO_LONG. */
   static int videoSeconds(MultipartFile file, Integer clientSeconds) {
      Optional<Double> measured;
      try (InputStream content = file.getInputStream()) {
         measured = Mp4Duration.read(content);
      } catch (IOException e) {
         measured = Optional.empty();
      }
      double seconds;
      if (measured.isPresent()) {
         seconds = measured.get();
      } else if (clientSeconds != null && clientSeconds > 0) {
         seconds = clientSeconds;
      } else {
         throw new BadRequestException("DURATION_REQUIRED", "Передайте длительность видео в секундах (durationSec)");
      }
      if (seconds > MAX_VIDEO_SECONDS + 0.5) {
         throw new BadRequestException("VIDEO_TOO_LONG", "Видео длиннее " + MAX_VIDEO_SECONDS + " секунд");
      }
      return Math.max(1, (int) Math.round(seconds));
   }

   /** Расширение файла: из имени (короткое, буквы и цифры) или по типу; имя от клиента в ключ не попадает. */
   static String extension(String originalFilename, String mimeType) {
      if (originalFilename != null) {
         int dot = originalFilename.lastIndexOf('.');
         String extension = dot >= 0 ? originalFilename.substring(dot + 1) : "";
         if (extension.matches("[A-Za-z0-9]{1,5}")) {
            return "." + extension.toLowerCase(Locale.ROOT);
         }
      }
      return switch (mimeType) {
         case "video/mp4" -> ".mp4";
         case "video/quicktime" -> ".mov";
         case "video/webm" -> ".webm";
         case "video/3gpp" -> ".3gp";
         default -> "";
      };
   }

   /** Вложения по id в порядке запроса — фото и видео; неизвестные id пропускаются. */
   @Transactional(readOnly = true)
   public List<MediaItemDto> items(Collection<Long> ids) {
      if (ids.isEmpty()) {
         return List.of();
      }
      Map<Long, Media> found = new LinkedHashMap<>();
      media.findAllById(ids).forEach(item -> found.put(item.getId(), item));
      return ids.stream().map(found::get).filter(Objects::nonNull).map(this::item).toList();
   }

   public MediaItemDto item(Media item) {
      if (item.isVideo()) {
         return new MediaItemDto(item.getId(), MediaKind.VIDEO, storage.urlFor(item.getVideoKey()),
               item.getKey320() == null ? null : storage.urlFor(item.getKey320()), item.getWidth(), item.getHeight(),
               item.getDurationSec());
      }
      return new MediaItemDto(item.getId(), MediaKind.PHOTO, storage.urlFor(item.getKey1080()),
            storage.urlFor(item.getKey320()), item.getWidth(), item.getHeight(), null);
   }

   @Transactional(readOnly = true)
   public List<Media> findAll(Collection<Long> ids) {
      return media.findAllById(ids);
   }

   /** Фото по id в порядке запроса; неизвестные id и видео пропускаются. */
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
         if (item != null && !item.isVideo()) {
            byId.put(id, dto(item));
         }
      });
      return byId;
   }

   /** Превью 320 px — для аватаров; null — фото нет. */
   @Transactional(readOnly = true)
   public String thumbUrl(Long mediaId) {
      return mediaId == null ? null : media.findById(mediaId).filter(item -> item.getKey320() != null)
            .map(item -> storage.urlFor(item.getKey320())).orElse(null);
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
      requireUsable(mediaIds, owners, purposes, false);
   }

   /** То же, но videoAllowed — можно и видео (вложения заявки на услугу). */
   @Transactional(readOnly = true)
   public void requireUsable(Collection<Long> mediaIds, Collection<Long> owners, Set<MediaPurpose> purposes,
                             boolean videoAllowed) {
      List<Media> found = media.findAllById(mediaIds);
      if (!videoAllowed && found.stream().anyMatch(Media::isVideo)) {
         throw new BadRequestException("BAD_PHOTO", "Сюда можно прикрепить только фото");
      }
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
