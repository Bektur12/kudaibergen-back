package kg.kudaibergen.auth.sms;

import java.time.Duration;

import kg.kudaibergen.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Локальный шлюз nikita.kg: XML-эндпоинт, ответ нас интересует только фактом 2xx. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "nikita")
public class NikitaSmsSender implements SmsSender {

   private static final Logger log = LoggerFactory.getLogger(NikitaSmsSender.class);

   private final AppProperties.Sms.Nikita config;
   private final RestClient restClient;

   public NikitaSmsSender(AppProperties properties) {
      this.config = properties.sms().nikita();
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(Duration.ofSeconds(5));
      factory.setReadTimeout(Duration.ofSeconds(10));
      this.restClient = RestClient.builder().requestFactory(factory).build();
   }

   @Override
   @Async("appTaskExecutor")
   public void send(String phone, String text) {
      String body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <message>
              <login>%s</login>
              <pwd>%s</pwd>
              <id>%s</id>
              <sender>%s</sender>
              <text>%s</text>
              <phones><phone>%s</phone></phones>
            </message>
            """.formatted(config.login(), config.password(), System.nanoTime(), config.sender(), text, phone);
      try {
         restClient.post()
               .uri(config.url())
               .contentType(MediaType.APPLICATION_XML)
               .body(body)
               .retrieve()
               .toBodilessEntity();
      } catch (RestClientException ex) {
         log.error("Не удалось отправить SMS на {}: {}", phone, ex.getMessage());
         throw ex;
      }
   }
}
