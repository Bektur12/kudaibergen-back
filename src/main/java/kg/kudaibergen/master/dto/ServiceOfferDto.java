package kg.kudaibergen.master.dto;

import java.time.Instant;

import kg.kudaibergen.master.entity.OfferAnswer;
import org.springframework.lang.Nullable;

/**
 * Отклик мастера: карточка на экране 37 (только «Могу помочь») и «мой ответ» в ленте мастера.
 * distanceM — от мастера до клиента («1,2 км»); chatId — «Написать»; master = null в ленте самого мастера.
 */
public record ServiceOfferDto(Long id, Long requestId, @Nullable MasterCardDto master, OfferAnswer answer,
                              @Nullable Integer priceFrom, @Nullable Instant availableAt, @Nullable String message,
                              int distanceM, @Nullable Long chatId, Instant createdAt) {
}
