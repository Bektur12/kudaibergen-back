package kg.kudaibergen.notification.push;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

   private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

   @Override
   public void send(List<String> deviceTokens, PushMessage message) {
      log.info("PUSH -> {} устройств: {} / {} {}", deviceTokens.size(), message.title(), message.body(),
            message.data());
   }
}
