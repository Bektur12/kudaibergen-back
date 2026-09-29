package kg.kudaibergen.request.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.catalog.dto.PartCardDto;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.request.entity.PartCondition;
import kg.kudaibergen.request.entity.ReplyAnswer;
import kg.kudaibergen.shop.dto.ShopCardDto;
import org.springframework.lang.Nullable;

/**
 * Ответ бокса: карточка на экране 07 (только «Есть») и «мой ответ» в ленте продавца.
 * editableUntil — до какого момента продавец может изменить ответ (потом — только в чате).
 * chatId — чат покупателя с боксом по этому запросу (есть у ответов «Есть»): кнопка «Написать».
 * part — приложенный товар из каталога продавца, photos — фото к ответу «Есть».
 */
public record ReplyDto(Long id, Long requestId, @Nullable ShopCardDto shop, ReplyAnswer answer, @Nullable PartCondition condition,
                       @Nullable String message, @Nullable Integer price, List<PhotoDto> photos, Instant createdAt, Instant updatedAt,
                       Instant editableUntil, @Nullable Long chatId, @Nullable PartCardDto part) {
}
