package kg.kudaibergen.chat.dto;

/** Пуш в /topic/chats/{id}/typing — чисто ephemeral, без истории. */
public record TypingEvent(Long userId, boolean typing) {
}
