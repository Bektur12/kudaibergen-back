package kg.kudaibergen.notification.push;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import kg.kudaibergen.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Firebase Cloud Messaging. Включается флагом app.fcm.enabled=true. */
@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
public class FcmPushSender implements PushSender {

   private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
   private static final int FCM_BATCH_LIMIT = 500;

   private final AppProperties.Fcm config;

   public FcmPushSender(AppProperties properties) {
      this.config = properties.fcm();
   }

   @PostConstruct
   void init() throws IOException {
      if (!FirebaseApp.getApps().isEmpty()) {
         return;
      }
      GoogleCredentials credentials;
      if (config.credentialsJson() != null && !config.credentialsJson().isBlank()) {
         credentials = GoogleCredentials.fromStream(
               new ByteArrayInputStream(config.credentialsJson().getBytes(StandardCharsets.UTF_8)));
      } else if (config.credentialsPath() == null || config.credentialsPath().isBlank()) {
         credentials = GoogleCredentials.getApplicationDefault();
      } else {
         try (InputStream stream = new FileInputStream(config.credentialsPath())) {
            credentials = GoogleCredentials.fromStream(stream);
         }
      }
      FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials).build());
      log.info("FCM инициализирован");
   }

   @Override
   public void send(List<String> deviceTokens, PushMessage message) {
      for (int from = 0; from < deviceTokens.size(); from += FCM_BATCH_LIMIT) {
         List<String> chunk = deviceTokens.subList(from, Math.min(from + FCM_BATCH_LIMIT, deviceTokens.size()));
         MulticastMessage multicast = MulticastMessage.builder()
               .addAllTokens(chunk)
               .setNotification(Notification.builder()
                     .setTitle(message.title())
                     .setBody(message.body())
                     .build())
               .putAllData(message.data())
               .setAndroidConfig(android(message))
               .setApnsConfig(apns(message))
               .build();
         try {
            var response = FirebaseMessaging.getInstance().sendEachForMulticast(multicast);
            if (response.getFailureCount() > 0) {
               log.warn("FCM: не доставлено {} из {}", response.getFailureCount(), chunk.size());
            }
         } catch (FirebaseMessagingException ex) {
            throw new IllegalStateException("Ошибка отправки пуша через FCM", ex);
         }
      }
   }

   private static AndroidConfig android(PushMessage message) {
      AndroidNotification.Builder notification = AndroidNotification.builder();
      if (message.sound()) {
         notification.setSound("default");
      }
      if (message.category() != null) {
         notification.setClickAction(message.category());
      }
      return AndroidConfig.builder().setNotification(notification.build()).build();
   }

   private static ApnsConfig apns(PushMessage message) {
      Aps.Builder aps = Aps.builder();
      if (message.sound()) {
         aps.setSound("default");
      }
      if (message.category() != null) {
         aps.setCategory(message.category());
      }
      return ApnsConfig.builder().setAps(aps.build()).build();
   }
}
