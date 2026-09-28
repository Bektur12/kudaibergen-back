package kg.kudaibergen.media.storage;

import java.io.InputStream;
import java.util.Map;
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

/**
 * Приватный бакет MinIO/S3: в базе ключ объекта, клиенту — presigned GET-ссылка. Ссылки кэшируются
 * на половину срока подписи, чтобы у клиента работал кэш картинок.
 */
@Service
@ConditionalOnProperty(name = "app.media.storage", havingValue = "s3")
public class S3MediaStorage implements MediaStorage {

   private static final Logger log = LoggerFactory.getLogger(S3MediaStorage.class);
   private static final int MAX_CACHED_URLS = 20_000;

   private final MinioClient client;
   private final AppProperties.Media.S3 s3;
   private final Map<String, SignedUrl> urlCache = new ConcurrentHashMap<>();

   private record SignedUrl(String url, long refreshAtMillis) {
   }

   public S3MediaStorage(AppProperties properties) {
      this.s3 = properties.media().s3();
      this.client = MinioClient.builder()
            .endpoint(s3.endpoint())
            .credentials(s3.accessKey(), s3.secretKey())
            .region(s3.region())
            .build();
   }

   @Override
   public String urlFor(String key) {
      if (key == null || key.isBlank()) {
         return null;
      }
      long now = System.currentTimeMillis();
      SignedUrl cached = urlCache.get(key);
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
         urlCache.put(key, new SignedUrl(url, now + s3.presignTtl().dividedBy(2).toMillis()));
      }
      return url;
   }

   private String sign(String key) {
      try {
         return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
               .method(Method.GET)
               .bucket(s3.bucket())
               .object(key)
               .expiry((int) s3.presignTtl().toSeconds(), TimeUnit.SECONDS)
               .build());
      } catch (Exception e) {
         log.error("Не удалось подписать ссылку на {}: {}", key, e.getMessage());
         return null;
      }
   }

   @Override
   public void put(String key, InputStream content, long size, String contentType) {
      try {
         client.putObject(PutObjectArgs.builder()
               .bucket(s3.bucket())
               .object(key)
               .stream(content, size, -1)
               .contentType(contentType)
               .headers(Map.of("Cache-Control", "private, max-age=31536000, immutable"))
               .build());
      } catch (Exception e) {
         throw new IllegalStateException("Не удалось загрузить файл в хранилище", e);
      }
   }
}
