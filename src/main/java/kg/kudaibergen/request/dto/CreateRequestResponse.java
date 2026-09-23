package kg.kudaibergen.request.dto;

import java.time.Instant;

/** sellersMatched приложение показывает как «отправлено 5 продавцам». */
public record CreateRequestResponse(Long id, int sellersMatched, Instant expiresAt) {
}
