package kg.kudaibergen.request.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.request.entity.RequestStatus;
import kg.kudaibergen.shop.dto.ShopCardDto;

/**
 * Статистика запроса для покупателя (32). durationMin — длина текущего окна ожидания (для полоски таймера),
 * remainingMin — сколько осталось (0 — время вышло). «Ещё не ответили» (silent) = delivered − have − notHave,
 * включая посмотревших. «Есть» — по времени ответа, с магазином и ценой; «Нет» — только ряд и контейнер.
 * Живое обновление — событие REQUEST_STATS в личном канале покупателя inbox:{userId}#{userId}.
 */
public record RequestStatsDto(Long requestId, RequestStatus status, Instant expiresAt, long durationMin,
                              long remainingMin, boolean canExtend, int extendedTimes, RecipientCounts counts,
                              List<Have> have, List<Place> notHave) {

   public record RecipientCounts(long delivered, long seen, long have, long notHave, long silent) {
   }

   /** row — код ряда («14», «Ю»), container — номер контейнера; на момент рассылки. */
   public record Have(ShopCardDto shop, String row, Integer container, Instant answeredAt, Integer price,
                      Long chatId) {
   }

   public record Place(String row, Integer container) {
   }
}
