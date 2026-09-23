package kg.kudaibergen.chat.dto;

/** Пуш в /topic/chats/{id}/presence при подключении/отключении собеседника. */
public record PresenceEvent(Long userId, boolean online) {
}
