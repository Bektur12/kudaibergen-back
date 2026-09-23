package kg.kudaibergen.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Текстовое сообщение. Фото/голосовые/видео — через POST /chats/{id}/messages/media. */
public record SendMessageRequest(
      @NotBlank(message = "Сообщение не может быть пустым") @Size(max = 4000) String body) {
}
