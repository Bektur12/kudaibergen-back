package kg.kudaibergen.master.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.master.entity.ServiceDuration;
import kg.kudaibergen.master.entity.ServiceWhen;
import kg.kudaibergen.master.entity.ServiceWhere;
import kg.kudaibergen.media.MediaItemDto;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.request.entity.RequestStatus;
import org.springframework.lang.Nullable;

/**
 * Заявка клиента (37). canWiden — можно расширить радиус (+5 км, до 50), canExtend — продлить (до 3 раз).
 * urgent — «Срочно» (эвакуатор). photos — только фото (как раньше); media — все вложения по порядку, фото и видео.
 */
public record ServiceRequestDetailDto(Long id, ServiceTypeDto service, ServiceCarDto car, String description,
                                      List<PhotoDto> photos, List<MediaItemDto> media, ServiceWhen when,
                                      @Nullable Instant atTime,
                                      ServiceWhere where, double lat, double lng, @Nullable String address,
                                      int radiusKm, RequestStatus status, ServiceRequestState state,
                                      ServiceDuration duration, Instant expiresAt, int extendedTimes,
                                      boolean canExtend, boolean canWiden, int recipientsCount, long seenCount,
                                      int canHelpCount, @Nullable Long closedWithMasterId, boolean urgent,
                                      Instant createdAt, @Nullable Instant closedAt) {
}
