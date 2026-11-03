package kg.kudaibergen.notification.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import kg.kudaibergen.common.config.ApnsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * APNs по HTTP/2 с JWT-ключом (.p8). Токен подписи живёт час, Apple просит обновлять не чаще раза в 20 минут —
 * держим 40. Включается флагом app.apns.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "app.apns.enabled", havingValue = "true")
public class ApnsPushSender {

   private static final Logger log = LoggerFactory.getLogger(ApnsPushSender.class);
   private static final Duration JWT_TTL = Duration.ofMinutes(40);

   private final ApnsProperties config;
   private final ObjectMapper json;
   private final Clock clock;
   private final PrivateKey key;
   private final HttpClient http = HttpClient.newBuilder()
         .version(HttpClient.Version.HTTP_2)
         .connectTimeout(Duration.ofSeconds(10))
         .build();

   private String jwt;
   private Instant jwtIssuedAt = Instant.EPOCH;

   public ApnsPushSender(ApnsProperties config, ObjectMapper json, Clock clock) {
      this.config = config;
      this.json = json;
      this.clock = clock;
      this.key = parseKey(config.key());
      log.info("APNs включён ({})", config.production() ? "production" : "sandbox");
   }

   public void send(List<String> deviceTokens, PushMessage message) {
      String body = payload(message);
      String host = config.production() ? "https://api.push.apple.com" : "https://api.sandbox.push.apple.com";
      int failed = 0;
      for (String token : deviceTokens) {
         HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/3/device/" + token))
               .timeout(Duration.ofSeconds(10))
               .header("authorization", "bearer " + jwt())
               .header("apns-topic", config.bundleId())
               .header("apns-push-type", "alert")
               .header("apns-priority", message.sound() ? "10" : "5")
               .POST(HttpRequest.BodyPublishers.ofString(body))
               .build();
         try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
               failed++;
               log.warn("APNs {}: {}", response.statusCode(), response.body());
            }
         } catch (java.io.IOException ex) {
            failed++;
            log.warn("APNs недоступен: {}", ex.getMessage());
         } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Отправка пуша через APNs прервана", ex);
         }
      }
      if (failed > 0) {
         log.warn("APNs: не доставлено {} из {}", failed, deviceTokens.size());
      }
   }

   /**
    * data кладём и в корень, и в body: expo-notifications на iOS отдаёт приложению content.data из body,
    * как его шлёт сервис Expo.
    */
   String payload(PushMessage message) {
      Map<String, Object> aps = new LinkedHashMap<>();
      aps.put("alert", Map.of("title", message.title(), "body", message.body()));
      if (message.sound()) {
         aps.put("sound", "default");
      }
      if (message.category() != null) {
         aps.put("category", message.category());
      }
      Map<String, Object> payload = new LinkedHashMap<>(message.data());
      payload.put("aps", aps);
      payload.put("body", message.data());
      try {
         return json.writeValueAsString(payload);
      } catch (JsonProcessingException ex) {
         throw new IllegalStateException(ex);
      }
   }

   synchronized String jwt() {
      Instant now = clock.instant();
      if (jwt == null || now.isAfter(jwtIssuedAt.plus(JWT_TTL))) {
         jwt = Jwts.builder()
               .header().keyId(config.keyId()).and()
               .issuer(config.teamId())
               .issuedAt(Date.from(now))
               .signWith(key, Jwts.SIG.ES256)
               .compact();
         jwtIssuedAt = now;
      }
      return jwt;
   }

   static PrivateKey parseKey(String pem) {
      if (pem == null || pem.isBlank()) {
         throw new IllegalStateException("app.apns.key пуст: нужен текст .p8");
      }
      String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
      try {
         return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
      } catch (Exception ex) {
         throw new IllegalStateException("app.apns.key: не удалось прочитать .p8", ex);
      }
   }
}
