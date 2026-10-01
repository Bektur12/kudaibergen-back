package kg.kudaibergen.chat.dto;

import java.time.Instant;

import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.chat.entity.MessageType;
import org.springframework.lang.Nullable;

/**
 * Строка списка чатов (16): аватар и имя собеседника, «Стойки передние · Ряд 14 · Бокс 12», последнее
 * сообщение, время, счётчик непрочитанных. requestClosed — строку рисовать серой с «запрос закрыт».
 * Чат с мастером — masterId и serviceRequestId (по заявке) вместо requestId.
 * Та же строка приходит в личный канал inbox при каждом изменении чата.
 */
public record ChatListItemDto(Long id, ChatSide mySide, String title, @Nullable String avatarUrl, @Nullable String subtitle,
                              @Nullable Long requestId, @Nullable Long serviceRequestId, @Nullable Long masterId,
                              boolean requestClosed, @Nullable LastMessageDto lastMessage, long unread,
                              boolean online, boolean blocked, Instant updatedAt) {

   /** Превью последнего сообщения: для фото и голосовых клиент рисует значок по type. */
   public record LastMessageDto(Long id, MessageType type, @Nullable String text, @Nullable String code, ChatSide side, boolean fromMe,
                                Instant at) {
   }
}
