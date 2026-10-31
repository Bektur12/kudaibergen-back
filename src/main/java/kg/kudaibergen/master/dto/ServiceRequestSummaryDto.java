package kg.kudaibergen.master.dto;

import java.time.Instant;

import kg.kudaibergen.request.entity.RequestStatus;
import org.springframework.lang.Nullable;

/** Строка «Мои заявки» на главной (05б). */
public record ServiceRequestSummaryDto(Long id, ServiceTypeDto service, String description, String carLabel,
                                       RequestStatus status, ServiceRequestState state, int canHelpCount,
                                       Instant createdAt, Instant expiresAt, @Nullable String thumbUrl) {
}
