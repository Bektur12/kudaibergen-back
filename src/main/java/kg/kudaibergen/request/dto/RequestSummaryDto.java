package kg.kudaibergen.request.dto;

import java.time.Instant;

import kg.kudaibergen.request.entity.RequestStatus;

/** Строка «Мои запросы» на главной (05). */
public record RequestSummaryDto(Long id, String text, String carLabel, RequestStatus status, RequestState state,
                                int haveCount, Instant createdAt) {
}
