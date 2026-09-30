package kg.kudaibergen.master.dto;

import java.time.Instant;

import kg.kudaibergen.request.entity.RequestStatus;

/**
 * Статистика заявки (37): получили, посмотрели, могут, не их профиль, молчат; таймер и радиус.
 * Живое обновление — событие SERVICE_REQUEST_STATS в личном канале клиента inbox:{userId}#{userId}.
 */
public record ServiceStatsDto(Long requestId, RequestStatus status, Instant expiresAt, long durationMin,
                              long remainingMin, boolean canExtend, int extendedTimes, int radiusKm,
                              ServiceRecipientCounts counts) {

   public record ServiceRecipientCounts(long delivered, long seen, long canHelp, long notMine, long silent) {
   }
}
