package kg.kudaibergen.request.dto;

import java.time.Instant;

import kg.kudaibergen.request.entity.RequestStatus;
import org.springframework.lang.Nullable;

/**
 * Строка «Мои запросы» на главной (03): «3 есть · от 2 900 с». expiresAt — для «Ищем ещё 18 мин» у активных,
 * minPrice — самая низкая цена среди «Есть» (null — ответов с ценой нет). thumbUrl — первое фото запроса для
 * плитки слева (null — фото нет, плитка с иконкой).
 */
public record RequestSummaryDto(Long id, String text, String carLabel, RequestStatus status, RequestState state,
                                int haveCount, @Nullable Integer minPrice, Instant createdAt, Instant expiresAt,
                                @Nullable String thumbUrl) {
}
