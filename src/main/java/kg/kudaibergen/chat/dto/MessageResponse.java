package kg.kudaibergen.chat.dto;

import java.time.Instant;
import java.util.function.UnaryOperator;

import kg.kudaibergen.chat.entity.Message;

public record MessageResponse(Long id, Long chatId, Long senderId, String body, String type, String mediaUrl,
                              String mimeType, Integer durationSeconds, Instant readAt, Instant createdAt) {

   /** {@code mediaUrl} в БД — ключ в хранилище; resolver превращает его в URL для клиента. */
   public static MessageResponse of(Message message, UnaryOperator<String> mediaUrl) {
      return new MessageResponse(message.getId(), message.getChatId(), message.getSenderId(),
            message.getBody(), message.getType(), mediaUrl.apply(message.getMediaUrl()), message.getMimeType(),
            message.getDurationSeconds(), message.getReadAt(), message.getCreatedAt());
   }
}
