package kg.kudaibergen.notification.push;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import kg.kudaibergen.common.config.ApnsProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApnsPushSenderTest {

   private final ObjectMapper json = new ObjectMapper();

   @Test
   void payloadCarriesAlertCategoryAndDataForExpo() throws Exception {
      ApnsPushSender sender = sender(newKey());

      JsonNode payload = json.readTree(sender.payload(new PushMessage("Новый запрос", "Фара Camry",
            Map.of("type", "NEW_REQUEST", "requestId", "7"), "NEW_REQUEST", true)));

      assertThat(payload.at("/aps/alert/title").asText()).isEqualTo("Новый запрос");
      assertThat(payload.at("/aps/sound").asText()).isEqualTo("default");
      assertThat(payload.at("/aps/category").asText()).isEqualTo("NEW_REQUEST");
      assertThat(payload.at("/body/requestId").asText()).isEqualTo("7");
      assertThat(payload.at("/type").asText()).isEqualTo("NEW_REQUEST");
   }

   @Test
   void quietPushHasNoSound() throws Exception {
      JsonNode payload = json.readTree(sender(newKey()).payload(new PushMessage("t", "b", Map.of(), null, false)));

      assertThat(payload.at("/aps").has("sound")).isFalse();
      assertThat(payload.at("/aps").has("category")).isFalse();
   }

   @Test
   void liveActivityStartCarriesAttributesStateAndStaleDate() throws Exception {
      PushMessage message = new PushMessage("Новый запрос · Camry 50", "Покупатель ищет: фара", Map.of(), "NEW_REQUEST",
            true).withLiveActivity(new PushMessage.LiveActivity("RequestActivityAttributes",
            Map.of("requestId", 7L, "carLabel", "Camry 50"), Map.of("status", "WAITING"),
            Instant.ofEpochSecond(1_800_000_000L)));

      JsonNode payload = json.readTree(sender(newKey()).liveActivityPayload(message));

      assertThat(payload.at("/aps/event").asText()).isEqualTo("start");
      assertThat(payload.at("/aps/attributes-type").asText()).isEqualTo("RequestActivityAttributes");
      assertThat(payload.at("/aps/attributes/requestId").asLong()).isEqualTo(7L);
      assertThat(payload.at("/aps/content-state/status").asText()).isEqualTo("WAITING");
      assertThat(payload.at("/aps/stale-date").asLong()).isEqualTo(1_800_000_000L);
      assertThat(payload.at("/aps/alert/title").asText()).isEqualTo("Новый запрос · Camry 50");
      assertThat(payload.at("/aps/alert/sound").asText()).isEqualTo("default");
      assertThat(payload.at("/aps/timestamp").isNumber()).isTrue();
   }

   @Test
   void jwtIsSignedWithKeyIdAndTeam() throws Exception {
      KeyPair key = newKey();

      var jws = Jwts.parser().verifyWith(key.getPublic()).build().parseSignedClaims(sender(key).jwt());

      assertThat(jws.getHeader().getKeyId()).isEqualTo("KEY123");
      assertThat(jws.getPayload().getIssuer()).isEqualTo("TEAM45");
   }

   private ApnsPushSender sender(KeyPair key) {
      String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(key.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
      return new ApnsPushSender(new ApnsProperties(true, "KEY123", "TEAM45", "kg.kudaiberdi.app", pem, true),
            json, Clock.fixed(Instant.now(), ZoneOffset.UTC));
   }

   private static KeyPair newKey() throws Exception {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      return generator.generateKeyPair();
   }
}
