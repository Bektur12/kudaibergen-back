package kg.kudaibergen.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.chat.entity.MessageType;

/**
 * Сообщение чата. side — сторона (BUYER / SHOP / SYSTEM), senderId — кто именно из бокса написал.
 * code — быстрый ответ (QUICK: ARRIVED, RESERVED, ROUTE, SOLD) или плашка (SYSTEM: PAY_AT_BOX,
 * REQUEST_CLOSED). payload — карточка ответа «Есть» (REPLY: replyId, condition, price) или маршрута
 * (ROUTE: location). read — другая сторона прочитала («прочитано»), иначе «доставлено».
 */
public record MessageDto(Long id, Long chatId, ChatSide side, Long senderId, MessageType type, String text,
                         String code, Map<String, Object> payload, String mediaUrl, String mimeType,
                         Integer durationSeconds, List<Double> waveform, String clientId, boolean read,
                         Instant createdAt) {
}
