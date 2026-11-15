package kg.kudaibergen.notification.push;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApsAlert;
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
         MulticastMessage.Builder multicast = MulticastMessage.builder()
               .addAllTokens(chunk)
               .putAllData(message.data())
               .setAndroidConfig(android(message))
               .setApnsConfig(apns(message));
         if (message.category() == null) {
            // обычный пуш: Android показывает его сам
            multicast.setNotification(Notification.builder().setTitle(message.title()).setBody(message.body()).build());
         }
         try {
            var response = FirebaseMessaging.getInstance().sendEachForMulticast(multicast.build());
            if (response.getFailureCount() > 0) {
               log.warn("FCM: не доставлено {} из {}", response.getFailureCount(), chunk.size());
            }
         } catch (FirebaseMessagingException ex) {
            throw new IllegalStateException("Ошибка отправки пуша через FCM", ex);
         }
      }
   }

   /** Каналы Android — создаёт приложение (src/lib/push.tsx), id должны совпадать. */
   static final String CHANNEL_REQUESTS = "requests";
   static final String CHANNEL_REQUESTS_SILENT = "requests_silent";
   static final String CHANNEL_IMPORTANT = "important";

   /**
    * Android. Пуш с кнопками (category: «Есть / Нет», «Написать / Как пройти») уходит data-only: тогда его рисует
    * само приложение (expo-notifications) — с кнопками, крупно и поверх экрана. Пуш с блоком notification Android
    * показывает сам, и кнопок у него нет. Высокий приоритет — чтобы будил телефон и всплывал сразу.
    */
   static AndroidConfig android(PushMessage message) {
      AndroidConfig.Builder config = AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH);
      if (message.category() != null) {
         return config.putAllData(androidData(message)).build();
      }
      AndroidNotification.Builder notification = AndroidNotification.builder()
            .setChannelId(CHANNEL_IMPORTANT)
            .setPriority(AndroidNotification.Priority.HIGH);
      if (message.sound()) {
         notification.setSound("default");
      }
      return config.setNotification(notification.build()).build();
   }

   /** Поля data-only пуша, которые читает expo-notifications: title, message, categoryId, channelId. */
   static Map<String, String> androidData(PushMessage message) {
      Map<String, String> data = new HashMap<>();
      data.put("title", message.title());
      if (message.body() != null) {
         data.put("message", message.body());
      }
      data.put("categoryId", message.category());
      // тихие часы и выключенный звук — канал без звука (на Android 8+ звук задаёт канал)
      data.put("channelId", message.sound() ? CHANNEL_REQUESTS : CHANNEL_REQUESTS_SILENT);
      return data;
   }

   private static ApnsConfig apns(PushMessage message) {
      Aps.Builder aps = Aps.builder();
      if (message.category() != null) {
         // без блока notification iOS берёт текст отсюда
         aps.setAlert(ApsAlert.builder().setTitle(message.title()).setBody(message.body()).build());
      }
      if (message.sound()) {
         aps.setSound("default");
      }
      if (message.category() != null) {
         aps.setCategory(message.category());
      }
      return ApnsConfig.builder().setAps(aps.build()).build();
   }
}
