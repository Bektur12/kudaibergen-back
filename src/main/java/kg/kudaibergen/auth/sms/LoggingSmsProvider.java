package kg.kudaibergen.auth.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Заглушка для dev и тестов: текст SMS пишется в лог, деньги не тратятся. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsProvider implements SmsProvider {

   private static final Logger log = LoggerFactory.getLogger(LoggingSmsProvider.class);

   @Override
   public void send(String phone, String text) {
      log.info("SMS -> {}: {}", phone, text);
   }
}
