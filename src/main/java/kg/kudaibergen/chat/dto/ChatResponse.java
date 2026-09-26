package kg.kudaibergen.chat.dto;

import java.time.Instant;

public record ChatResponse(Long id, Long requestId, Long buyerId, Long storeId, String storeName,
                           String lastMessage, Instant lastMessageAt, boolean hasUnread, Instant createdAt,
                           boolean otherOnline, Instant otherLastSeenAt, String channel) {
}
