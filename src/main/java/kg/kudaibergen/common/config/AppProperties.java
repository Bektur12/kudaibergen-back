package kg.kudaibergen.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** Все настройки домена в одном месте (префикс app.*). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Otp otp, Sms sms, Fcm fcm, Market market, Requests requests,
                            Centrifugo centrifugo, Media media) {

   /**
    * Живой чат идёт через Centrifugo: apiUrl и apiKey — Server API (публикация и presence),
    * tokenSecret — HMAC-ключ токенов подключения и подписки клиента (не app.jwt.secret).
    */
   public record Centrifugo(String apiUrl, String apiKey, String tokenSecret, Duration tokenTtl) {
   }

   /**
    * Вложения чата. storage: local — папка uploadDir, отдаётся по /media/** (dev); s3 — приватный
    * бакет MinIO/S3 и presigned-ссылки. Голосовые — не длиннее maxVoiceSeconds (ТЗ 11.1).
    */
   public record Media(String storage, String uploadDir, S3 s3, DataSize maxPhotoSize, DataSize maxVoiceSize,
                       DataSize maxVideoSize, int maxVoiceSeconds) {

      public record S3(String endpoint, String accessKey, String secretKey, String bucket, String region,
                       Duration presignTtl) {
      }
   }

   /**
    * Запросы «Найти запчасть» (ТЗ 4.3–4.5, 10.2): лимиты покупателя, таймер «никто не ответил»,
    * срок жизни без действий, окно правки ответа продавцом.
    */
   public record Requests(int maxOpen, int maxPerDay, Duration noReplyAfter, Duration expireAfter,
                          Duration replyEditWindow) {
   }

   /** qrBaseUrl — префикс ссылки в QR-наклейках: {qrBaseUrl}{token} открывает приложение. */
   public record Market(String qrBaseUrl) {
   }

   public record Jwt(String secret, Duration accessTtl, Duration refreshTtl) {
   }

   /** Код входа: TTL, пауза до повторной отправки, попытки и блокировка номера, лимиты отправки на номер и IP. */
   public record Otp(Duration codeTtl, Duration resendInterval, int maxAttempts, Duration blockDuration, int phoneLimit,
                     int ipLimit, Duration limitWindow, String hashSecret, boolean exposeCode, String fixedCode) {

      public boolean hasFixedCode() {
         return fixedCode != null && !fixedCode.isBlank();
      }
   }

   public record Sms(String provider, Nikita nikita) {

      public record Nikita(String url, String login, String password, String sender) {
      }
   }

   public record Fcm(boolean enabled, String credentialsPath, String credentialsJson) {
   }
}
