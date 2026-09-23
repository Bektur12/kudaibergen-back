package kg.kudaibergen.chat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import kg.kudaibergen.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * S3-совместимый бакет (Railway Bucket и др.). Бакет приватный: в БД хранится ключ объекта,
 * клиенту при каждой выдаче сообщения отдаётся presigned GET-ссылка с ограниченным сроком жизни.
 */
@Service
@ConditionalOnProperty(name = "app.media.storage", havingValue = "s3")
public class S3ChatMediaStorage extends ChatMediaStorage {

   private static final Logger log = LoggerFactory.getLogger(S3ChatMediaStorage.class);

   private final MinioClient client;
   private final AppProperties.Media.S3 config;

   /** Кэш presigned-ссылок: пока запись жива, клиент получает тот же URL, а значит и кэш картинок
    * на его стороне работает. Обновляем на половине срока жизни подписи, чтобы ссылка не протухла у клиента. */
   private record SignedUrl(String url, long refreshAtMillis) {
   }

   private static final int MAX_CACHED_URLS = 20_000;
   private final Map<String, SignedUrl> urlCache = new ConcurrentHashMap<>();

   public S3ChatMediaStorage(AppProperties properties) {
      super(properties);
      this.config = properties.media().s3();
      this.client = MinioClient.builder()
            .endpoint(config.endpoint())
            .credentials(config.accessKey(), config.secretKey())
            .region(config.region())
            .build();
   }

   @Override
   public String urlFor(String key) {
      if (key == null || key.isBlank()) {
         return null;
      }
      SignedUrl cached = urlCache.get(key);
      long now = System.currentTimeMillis();
      if (cached != null && cached.refreshAtMillis() > now) {
         return cached.url();
      }
      String url = sign(key);
      if (url != null) {
         if (urlCache.size() >= MAX_CACHED_URLS) {
            urlCache.values().removeIf(entry -> entry.refreshAtMillis() <= now);
            if (urlCache.size() >= MAX_CACHED_URLS) {
               urlCache.clear();
            }
         }
         urlCache.put(key, new SignedUrl(url, now + config.presignTtl().dividedBy(2).toMillis()));
      }
      return url;
   }

   private String sign(String key) {
      try {
         return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
               .method(Method.GET)
               .bucket(config.bucket())
               .object(key)
               .expiry((int) config.presignTtl().toSeconds(), TimeUnit.SECONDS)
               .build());
      } catch (Exception e) {
         log.error("Не удалось подписать ссылку на {}: {}", key, e.getMessage());
         return null;
      }
   }

   @Override
   protected String save(MultipartFile file, String type, String extension) {
      String key = "chat/" + type.toLowerCase() + "/" + UUID.randomUUID() + extension;
      try {
         client.putObject(PutObjectArgs.builder()
               .bucket(config.bucket())
               .object(key)
               .stream(file.getInputStream(), file.getSize(), -1)
               .contentType(file.getContentType())
               .headers(Map.of("Cache-Control", "private, max-age=31536000, immutable"))
               .build());
      } catch (Exception e) {
         throw new IllegalStateException("Не удалось загрузить файл в хранилище", e);
      }
      return key;
   }
}
