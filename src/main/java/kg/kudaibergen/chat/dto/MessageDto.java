package kg.kudaibergen.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.chat.entity.MessageType;
import org.springframework.lang.Nullable;

/**
 * Сообщение чата. side — сторона (BUYER / SHOP / SYSTEM), senderId — кто именно из бокса написал.
 * code — быстрый ответ (QUICK: ARRIVED, RESERVED, ROUTE, SOLD) или плашка (SYSTEM: PAY_AT_BOX,
 * REQUEST_CLOSED). payload — карточка ответа «Есть» (REPLY: replyId, condition, price, partId), маршрута
 * (ROUTE: location) или товара (PART: partId, price, mainPhoto). read — другая сторона прочитала («прочитано»), иначе «доставлено».
 */
public record MessageDto(Long id, Long chatId, ChatSide side, @Nullable Long senderId, MessageType type, @Nullable String text,
                         @Nullable String code, @Nullable Map<String, Object> payload, @Nullable String mediaUrl, @Nullable String mimeType,
                         @Nullable Integer durationSeconds, @Nullable List<Double> waveform, @Nullable String clientId, boolean read,
                         Instant createdAt) {
}
