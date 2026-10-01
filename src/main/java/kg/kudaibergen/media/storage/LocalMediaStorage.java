package kg.kudaibergen.media.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import kg.kudaibergen.common.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Dev: файлы в локальной папке, отдаются статикой по /media/** (см. MediaWebConfig). */
@Service
@ConditionalOnProperty(name = "app.media.storage", havingValue = "local", matchIfMissing = true)
public class LocalMediaStorage implements MediaStorage {

   private final Path root;

   /**
    * Адрес сервера для ссылок на файлы (MEDIA_PUBLIC_URL, например http://192.168.1.10:8080): телефон не может
    * открыть относительный «/media/…». Пусто — ссылки относительные, клиент дописывает адрес API сам.
    */
   private final String publicUrl;

   @Autowired
   public LocalMediaStorage(AppProperties properties, @Value("${app.media.public-url:}") String publicUrl) {
      this.root = Path.of(properties.media().uploadDir()).toAbsolutePath().normalize();
      this.publicUrl = publicUrl == null ? "" : publicUrl.strip().replaceAll("/+$", "");
   }

   public LocalMediaStorage(AppProperties properties) {
      this(properties, "");
   }

   @Override
   public String urlFor(String key) {
      return key == null ? null : publicUrl + "/media/" + key;
   }

   @Override
   public void delete(String key) {
      Path target = root.resolve(key).normalize();
      if (!target.startsWith(root)) {
         throw new IllegalArgumentException("Ключ вне папки хранилища: " + key);
      }
      try {
         Files.deleteIfExists(target);
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось удалить файл " + key, e);
      }
   }

   @Override
   public void put(String key, InputStream content, long size, String contentType) {
      Path target = root.resolve(key).normalize();
      if (!target.startsWith(root)) {
         throw new IllegalArgumentException("Ключ вне папки хранилища: " + key);
      }
      try {
         Files.createDirectories(target.getParent());
         Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось сохранить файл", e);
      }
   }
}
