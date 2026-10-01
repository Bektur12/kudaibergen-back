package kg.kudaibergen.media.storage;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Optional;

import kg.kudaibergen.common.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Dev: файлы в локальной папке, отдаются статикой по /media/** (см. MediaWebConfig). */
@Service
@ConditionalOnProperty(name = "app.media.storage", havingValue = "local", matchIfMissing = true)
public class LocalMediaStorage implements MediaStorage {

   private final Path root;

   /**
    * Адрес сервера для ссылок на файлы: телефон не может открыть относительный «/media/…». Задан MEDIA_PUBLIC_URL —
    * он (прод). Иначе — адрес, по которому пришёл запрос (телефон ходит на http://192.168.x.x:8080 — тот же адрес
    * будет в ссылке); вне запроса (события Centrifugo, пуши) — адрес этого компьютера в локальной сети.
    */
   private final String publicUrl;
   private final int port;
   private volatile String lanUrl;

   @Autowired
   public LocalMediaStorage(AppProperties properties, @Value("${app.media.public-url:}") String publicUrl,
                            @Value("${server.port:8080}") int port) {
      this.root = Path.of(properties.media().uploadDir()).toAbsolutePath().normalize();
      this.publicUrl = publicUrl == null ? "" : publicUrl.strip().replaceAll("/+$", "");
      this.port = port;
   }

   public LocalMediaStorage(AppProperties properties) {
      this(properties, "", 8080);
   }

   @Override
   public String urlFor(String key) {
      return key == null ? null : baseUrl() + "/media/" + key;
   }

   String baseUrl() {
      if (!publicUrl.isEmpty()) {
         return publicUrl;
      }
      if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
         return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
      }
      String detected = lanUrl;
      if (detected == null) {
         detected = lanAddress().map(address -> "http://" + address + ":" + port).orElse("");
         lanUrl = detected;
      }
      return detected;
   }

   /** IPv4 этого компьютера в локальной сети (192.168.x.x, 10.x.x.x); нет — пусто. */
   static Optional<String> lanAddress() {
      try {
         for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
               continue;
            }
            for (InetAddress address : Collections.list(network.getInetAddresses())) {
               if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                  return Optional.of(address.getHostAddress());
               }
            }
         }
      } catch (SocketException e) {
         return Optional.empty();
      }
      return Optional.empty();
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
