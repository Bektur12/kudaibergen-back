package kg.kudaibergen.master.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.master.entity.ServiceWhen;
import kg.kudaibergen.master.entity.ServiceWhere;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.request.entity.RequestStatus;
import org.springframework.lang.Nullable;

/**
 * Заявка в ленте мастера (39): услуга, машина, что случилось, когда и где, «1,2 км от вас», таймер.
 * Телефон клиента не показывается; buyerName = null — «Клиент». dealHere — клиент выбрал этого мастера.
 */
public record MasterFeedItemDto(Long id, ServiceTypeDto service, ServiceCarDto car, String description,
                                List<PhotoDto> photos, ServiceWhen when, @Nullable Instant atTime, ServiceWhere where,
                                @Nullable String address, double lat, double lng, int distanceM,
                                @Nullable String buyerName, RequestStatus status, boolean dealHere, boolean urgent,
                                Instant createdAt, Instant notifiedAt, Instant expiresAt, boolean seen,
                                @Nullable ServiceOfferDto myOffer) {
}
