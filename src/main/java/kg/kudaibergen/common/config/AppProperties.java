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
                     boolean exposeCode, Nikita nikita) {

      public record Nikita(String url, String login, String password, String sender) {
      }
   }

   public record RequestLimits(Duration normalTtl, Duration urgentTtl, Duration extendBy, int dailyLimitPerBuyer) {
   }

   public record Notification(int dispatchBatchSize, int maxAttempts, Duration batchingWindow, int batchingThreshold) {
   }

   public record Fcm(boolean enabled, String credentialsPath) {
   }

   /** Медиа-сообщения в чате (фото/голосовые/видео): локальный диск в dev, позже — S3/GCS. */
   public record Media(String uploadDir, DataSize maxPhotoSize, DataSize maxVoiceSize, DataSize maxVideoSize) {
   }
}
