package kg.kudaibergen.chat.dto;

import java.time.Instant;

/** Пуш в канал chat:{id}#... (ChatEvent.READ) — отправителю, что собеседник прочитал его сообщения. */
public record ReadEvent(Long userId, Instant readAt) {
}
