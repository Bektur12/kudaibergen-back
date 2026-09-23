package kg.kudaibergen.notification;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.PartCategory;
import kg.kudaibergen.notification.entity.NotificationOutbox;
import kg.kudaibergen.notification.entity.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Запись уведомлений в outbox. Вызывается внутри бизнес-транзакции:
 * если транзакция откатится, уведомлений тоже не будет.
 */
@Service
public class OutboxService {

   private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

   private final NotificationOutboxRepository outbox;
   private final ObjectMapper objectMapper;

   public OutboxService(NotificationOutboxRepository outbox, ObjectMapper objectMapper) {
      this.outbox = outbox;
      this.objectMapper = objectMapper;
   }

   /** Веерная рассылка: по строке на каждого продавца, сам пуш уйдёт шедулером. */
   @Transactional
   public void enqueueNewRequest(Long requestId, PartCategory category, boolean urgent, List<Long> userIds) {
      if (userIds.isEmpty()) {
         return;
      }
      String title = urgent ? "СРОЧНО: " + category.title() : "Новый запрос: " + category.title();
      String body = urgent
            ? "Срочный запрос в вашей категории — ответьте первым"
            : "Покупатель ищет запчасть в вашей категории";
      String payload = payload(Map.of(
            "type", NotificationType.NEW_REQUEST.name(),
            "requestId", String.valueOf(requestId),
            "category", category.name(),
            "urgent", String.valueOf(urgent)));

      List<NotificationOutbox> rows = userIds.stream()
            .map(userId -> new NotificationOutbox(userId, title, body, payload))
            .toList();
      outbox.saveAll(rows);
      log.debug("В outbox добавлено {} уведомлений о запросе {}", rows.size(), requestId);
   }

   @Transactional
   public void enqueueNewMessage(Long recipientUserId, Long chatId, String senderName, String text) {
      outbox.save(new NotificationOutbox(recipientUserId,
            senderName,
            text.length() > 200 ? text.substring(0, 200) + "…" : text,
            payload(Map.of(
                  "type", NotificationType.NEW_MESSAGE.name(),
                  "chatId", String.valueOf(chatId)))));
   }

   private String payload(Map<String, String> values) {
      try {
         return objectMapper.writeValueAsString(new LinkedHashMap<>(values));
      } catch (JsonProcessingException ex) {
         throw new IllegalStateException("Не удалось сериализовать payload уведомления", ex);
      }
   }
}
