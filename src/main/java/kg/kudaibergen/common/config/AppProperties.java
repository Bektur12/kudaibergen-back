package kg.kudaibergen.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Все настройки домена в одном месте (префикс app.*). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Otp otp, Sms sms, Fcm fcm, Market market) {

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
