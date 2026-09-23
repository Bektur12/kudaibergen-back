package kg.kudaibergen.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.chat.entity.Message;

public record MessageResponse(Long id, Long chatId, Long senderId, String body, String type, String mediaUrl,
                              String mimeType, Integer durationSeconds, List<Double> waveform, Instant readAt,
                              Instant createdAt) {

   private static final ObjectMapper JSON = new ObjectMapper();

   /** {@code mediaUrl} в БД — ключ в хранилище; resolver превращает его в URL для клиента. */
   public static MessageResponse of(Message message, UnaryOperator<String> mediaUrl) {
      return new MessageResponse(message.getId(), message.getChatId(), message.getSenderId(),
            message.getBody(), message.getType(), mediaUrl.apply(message.getMediaUrl()), message.getMimeType(),
            message.getDurationSeconds(), parseWaveform(message.getWaveform()), message.getReadAt(),
            message.getCreatedAt());
   }

   private static List<Double> parseWaveform(String json) {
      if (json == null || json.isBlank()) {
         return null;
      }
      try {
         return JSON.readValue(json, new TypeReference<List<Double>>() {
         });
      } catch (Exception e) {
         return null;
      }
   }
}
