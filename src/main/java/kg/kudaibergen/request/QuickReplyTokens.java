package kg.kudaibergen.request;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import kg.kudaibergen.common.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Ключ ответа с экрана блокировки (Live Activity, iPhone): кнопка «Нет» отвечает без входа в приложение — у
 * карточки нет токена сессии. Ключ подписан HMAC и годится только для одного запроса, одного магазина и
 * одного продавца и только до конца запроса. Формат: base64url(«requestId.shopId.userId.expiresEpoch»).base64url(hmac).
 */
@Component
public class QuickReplyTokens {

   private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
   private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

   private final byte[] secret;
   private final Clock clock;

   public QuickReplyTokens(AppProperties properties, Clock clock) {
      // свой ключ, выведенный из секрета JWT: подпись ключа ответа нельзя выдать за токен сессии и наоборот
      this.secret = hmac(properties.jwt().secret().getBytes(StandardCharsets.UTF_8),
            "quick-reply-v1".getBytes(StandardCharsets.UTF_8));
      this.clock = clock;
   }

   public String issue(Long requestId, Long shopId, Long userId, Instant expiresAt) {
      byte[] payload = (requestId + "." + shopId + "." + userId + "." + expiresAt.getEpochSecond())
            .getBytes(StandardCharsets.UTF_8);
      return ENCODER.encodeToString(payload) + "." + ENCODER.encodeToString(hmac(secret, payload));
   }

   /** Кто отвечает: id продавца, если ключ подлинный, от этого запроса и не истёк; иначе null. */
   public Long verify(String token, Long requestId) {
      if (token == null) {
         return null;
      }
      int dot = token.indexOf('.');
      if (dot <= 0 || dot == token.length() - 1) {
         return null;
      }
      try {
         byte[] payload = DECODER.decode(token.substring(0, dot));
         byte[] signature = DECODER.decode(token.substring(dot + 1));
         if (!MessageDigest.isEqual(signature, hmac(secret, payload))) {
            return null;
         }
         String[] parts = new String(payload, StandardCharsets.UTF_8).split("\\.");
         if (parts.length != 4 || !parts[0].equals(String.valueOf(requestId))) {
            return null;
         }
         if (clock.instant().getEpochSecond() > Long.parseLong(parts[3])) {
            return null;
         }
         return Long.parseLong(parts[2]);
      } catch (IllegalArgumentException malformed) {
         return null;
      }
   }

   private static byte[] hmac(byte[] key, byte[] data) {
      try {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(key, "HmacSHA256"));
         return mac.doFinal(data);
      } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
         throw new IllegalStateException(ex);
      }
   }
}
