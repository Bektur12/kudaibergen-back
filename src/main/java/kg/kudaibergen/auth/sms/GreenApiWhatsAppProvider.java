package kg.kudaibergen.auth.sms;

import java.time.Duration;
import java.util.Map;

import kg.kudaibergen.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Пока нет SMS-шлюза — коды и прочие «SMS» уходят сообщением в WhatsApp через Green-API:
 * {@code POST {url}/waInstance{id}/sendMessage/{token}} с {@code {chatId: "996555123456@c.us", message}}.
 * Отправитель — номер, подключённый к инстансу по QR-коду в кабинете green-api.com.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "whatsapp")
public class GreenApiWhatsAppProvider implements SmsProvider {

   private static final Logger log = LoggerFactory.getLogger(GreenApiWhatsAppProvider.class);

   private final AppProperties.Sms.GreenApi config;
   private final RestClient restClient;

   public GreenApiWhatsAppProvider(AppProperties properties) {
      this.config = properties.sms().greenApi();
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(5));
      factory.setReadTimeout(Duration.ofSeconds(15));
      this.restClient = RestClient.builder().baseUrl(config.url()).requestFactory(factory).build();
   }

   @Override
   @Async("appTaskExecutor")
   public void send(String phone, String text) {
      try {
         restClient.post()
               .uri("/waInstance{id}/sendMessage/{token}", config.idInstance(), config.token())
               .contentType(MediaType.APPLICATION_JSON)
               .body(Map.of("chatId", chatId(phone), "message", text))
               .retrieve()
               .toBodilessEntity();
      } catch (RestClientException ex) {
         log.error("Не удалось отправить WhatsApp на {}: {}", phone, ex.getMessage());
      }
   }

   /** +996555123456 → 996555123456@c.us */
   static String chatId(String phone) {
      return phone.replaceAll("\\D", "") + "@c.us";
   }
}
