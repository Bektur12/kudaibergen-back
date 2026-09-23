package kg.kudaibergen.auth.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev-режим: код видно в логах, деньги на SMS не тратятся. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

   private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

   @Override
   public void send(String phone, String text) {
      log.info("SMS -> {}: {}", phone, text);
   }
}
