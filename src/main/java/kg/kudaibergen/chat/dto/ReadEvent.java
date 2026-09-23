package kg.kudaibergen.chat.dto;

import java.time.Instant;

/** Пуш в /topic/chats/{id}/read — отправителю, что собеседник прочитал его сообщения. */
public record ReadEvent(Long userId, Instant readAt) {
}
