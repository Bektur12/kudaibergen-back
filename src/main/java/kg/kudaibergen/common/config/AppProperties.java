package kg.kudaibergen.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Все настройки домена в одном месте (префикс app.*). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Sms sms, RequestLimits request, Notification notification, Fcm fcm,
                            Media media) {

   public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {
   }

   public record Sms(String provider, Duration codeTtl, int maxAttempts, Duration resendInterval,
                     boolean exposeCode, String fixedCode, Nikita nikita) {

      public record Nikita(String url, String login, String password, String sender) {
      }
   }

   public record RequestLimits(Duration normalTtl, Duration urgentTtl, Duration extendBy, int dailyLimitPerBuyer) {
   }

   public record Notification(int dispatchBatchSize, int maxAttempts, Duration batchingWindow, int batchingThreshold) {
   }

   public record Fcm(boolean enabled, String credentialsPath, String credentialsJson) {
   }

   /** Медиа-сообщения в чате (фото/голосовые/видео): storage = local (диск, dev) или s3 (бакет). */
   public record Media(String storage, String uploadDir, S3 s3, DataSize maxPhotoSize, DataSize maxVoiceSize,
                       DataSize maxVideoSize) {

      public record S3(String endpoint, String accessKey, String secretKey, String bucket, String region,
                       Duration presignTtl) {
      }
   }
}
