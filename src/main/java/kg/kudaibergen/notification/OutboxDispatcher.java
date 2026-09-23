package kg.kudaibergen.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.notification.entity.NotificationOutbox;
import kg.kudaibergen.notification.entity.NotificationType;
import kg.kudaibergen.notification.push.PushMessage;
import kg.kudaibergen.notification.push.PushSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Разгребает outbox каждые 5 секунд.
 * Батчинг: если одному продавцу за окно (15 минут) прилетело больше трёх новых запросов —
 * уходит одно уведомление «8 новых запросов, 3 срочных» вместо восьми отдельных.
 */
@Component
public class OutboxDispatcher {

   private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

   private final NotificationOutboxRepository outbox;
   private final DeviceTokenRepository deviceTokens;
   private final PushSender pushSender;
   private final ObjectMapper objectMapper;
   private final AppProperties.Notification config;

   public OutboxDispatcher(NotificationOutboxRepository outbox, DeviceTokenRepository deviceTokens,
                           PushSender pushSender, ObjectMapper objectMapper, AppProperties properties) {
      this.outbox = outbox;
      this.deviceTokens = deviceTokens;
      this.pushSender = pushSender;
      this.objectMapper = objectMapper;
      this.config = properties.notification();
   }

   @Scheduled(fixedDelayString = "PT5S")
   @Transactional
   public void dispatch() {
      List<NotificationOutbox> unsent = outbox.findUnsent((short) config.maxAttempts(),
            Limit.of(config.dispatchBatchSize()));
      if (unsent.isEmpty()) {
         return;
      }

      Map<Long, List<NotificationOutbox>> byUser = unsent.stream()
            .collect(Collectors.groupingBy(NotificationOutbox::getUserId, LinkedHashMap::new, Collectors.toList()));

      for (var entry : byUser.entrySet()) {
         List<String> tokens = deviceTokens.findTokensByUserId(entry.getKey());
         if (tokens.isEmpty()) {
            // устройств нет — уведомление уже не доставить, чтобы очередь не росла, закрываем
            entry.getValue().forEach(NotificationOutbox::markSent);
            continue;
         }
         sendForUser(tokens, entry.getValue());
      }
   }

   private void sendForUser(List<String> tokens, List<NotificationOutbox> notifications) {
      List<NotificationOutbox> newRequests = new ArrayList<>();
      List<NotificationOutbox> rest = new ArrayList<>();
      for (NotificationOutbox notification : notifications) {
         if (typeOf(notification) == NotificationType.NEW_REQUEST) {
            newRequests.add(notification);
         } else {
            rest.add(notification);
         }
      }

      List<NotificationOutbox> batchable = withinWindow(newRequests);
      if (batchable.size() > config.batchingThreshold()) {
         sendBatched(tokens, batchable);
         newRequests.removeAll(batchable);
      }

      for (NotificationOutbox notification : newRequests) {
         sendOne(tokens, notification);
      }
      for (NotificationOutbox notification : rest) {
         sendOne(tokens, notification);
      }
   }

   /** Уведомления, попавшие в окно батчинга, считая от самого старого непосланного. */
   private List<NotificationOutbox> withinWindow(List<NotificationOutbox> notifications) {
      if (notifications.isEmpty()) {
         return List.of();
      }
      Duration window = config.batchingWindow();
      Instant oldest = notifications.stream()
            .map(NotificationOutbox::getCreatedAt)
            .min(Instant::compareTo)
            .orElse(Instant.now());
      return notifications.stream()
            .filter(notification -> !notification.getCreatedAt().isAfter(oldest.plus(window)))
            .toList();
   }

   private void sendBatched(List<String> tokens, List<NotificationOutbox> notifications) {
      long urgent = notifications.stream().filter(this::isUrgent).count();
      String body = urgent > 0
            ? notifications.size() + " новых запросов, " + urgent + " срочных"
            : notifications.size() + " новых запросов";
      PushMessage message = new PushMessage("Новые запросы", body,
            Map.of("type", NotificationType.NEW_REQUEST.name(), "count", String.valueOf(notifications.size())));
      try {
         pushSender.send(tokens, message);
         notifications.forEach(NotificationOutbox::markSent);
      } catch (RuntimeException ex) {
         log.warn("Не удалось отправить пакет уведомлений: {}", ex.getMessage());
         notifications.forEach(NotificationOutbox::registerAttempt);
      }
   }

   private void sendOne(List<String> tokens, NotificationOutbox notification) {
      try {
         pushSender.send(tokens, new PushMessage(notification.getTitle(), notification.getBody(),
               data(notification)));
         notification.markSent();
      } catch (RuntimeException ex) {
         log.warn("Не удалось отправить уведомление {}: {}", notification.getId(), ex.getMessage());
         notification.registerAttempt();
      }
   }

   private boolean isUrgent(NotificationOutbox notification) {
      return "true".equals(data(notification).get("urgent"));
   }

   private NotificationType typeOf(NotificationOutbox notification) {
      String type = data(notification).get("type");
      try {
         return type == null ? null : NotificationType.valueOf(type);
      } catch (IllegalArgumentException unknown) {
         return null;
      }
   }

   private Map<String, String> data(NotificationOutbox notification) {
      if (notification.getPayload() == null || notification.getPayload().isBlank()) {
         return Map.of();
      }
      try {
         return objectMapper.readValue(notification.getPayload(),
               objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
      } catch (Exception ex) {
         log.warn("Некорректный payload уведомления {}", notification.getId());
         return Map.of();
      }
   }
}
