package kg.kudaibergen.chat.dto;

import java.time.Instant;

import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.shop.dto.ShopCardDto;

/**
 * Открытый чат (08 — покупатель, 13 — продавец). Шапка: магазин «Ряд 14 · Бокс 12 · в сети» или покупатель
 * «имя · запрос · машина». request — закреплённый запрос. mySide — от чьего имени пишет текущий пользователь.
 * otherReadMessageId — до какого сообщения прочитала другая сторона (галочки). channel — канал Centrifugo,
 * подписка на него — по токену из GET /chats/{id}/subscription-token.
 */
public record ChatDto(Long id, ChatSide mySide, ShopCardDto shop, BuyerDto buyer, PinnedRequestDto request,
                      boolean online, Instant lastSeenAt, boolean blockedByMe, boolean blockedByOther,
                      boolean canWrite, long unread, long otherReadMessageId, String channel) {

   /** Покупатель для продавца: имя и аватар, телефон не показываем (ТЗ 14). null — не заполнено. */
   public record BuyerDto(Long id, String name, String avatarUrl) {
   }

   /** «Стойки передние · Toyota Camry 50 · 2012», open = false — запрос закрыт или истёк. */
   public record PinnedRequestDto(Long id, String text, String carLabel, String status, boolean open,
                                  boolean soldHere) {
   }
}
