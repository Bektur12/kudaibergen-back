package kg.kudaibergen.chat;

import java.util.UUID;
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
               .build());
      } catch (Exception e) {
         throw new IllegalStateException("Не удалось загрузить файл в хранилище", e);
      }
      return key;
   }
}
