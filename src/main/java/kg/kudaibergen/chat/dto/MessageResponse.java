package kg.kudaibergen.chat.dto;

import java.time.Instant;

import kg.kudaibergen.chat.entity.Message;

public record MessageResponse(Long id, Long chatId, Long senderId, String body, String type, String mediaUrl,
                              String mimeType, Integer durationSeconds, Instant readAt, Instant createdAt) {

   public static MessageResponse of(Message message) {
      return new MessageResponse(message.getId(), message.getChatId(), message.getSenderId(),
            message.getBody(), message.getType(), message.getMediaUrl(), message.getMimeType(),
            message.getDurationSeconds(), message.getReadAt(), message.getCreatedAt());
   }
}
