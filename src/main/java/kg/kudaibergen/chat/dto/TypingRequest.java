package kg.kudaibergen.chat.dto;

/** То, что клиент шлёт через STOMP SEND на /app/chats/{id}/typing. */
public record TypingRequest(boolean typing) {
}
