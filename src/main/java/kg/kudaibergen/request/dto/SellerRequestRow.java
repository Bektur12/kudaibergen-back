package kg.kudaibergen.request.dto;

import java.time.Instant;

import kg.kudaibergen.common.PartCategory;

/**
 * Строка ленты продавца. Группировку по категориям («23 запроса на колодки»)
 * делает клиент — бэкенд отдаёт плоский список с полем category.
 */
public record SellerRequestRow(Long requestId, PartCategory category, String description, String car,
                               Integer budgetMin, Integer budgetMax, String currency, String city,
                               boolean isUrgent, int offerCount, Instant createdAt, Instant expiresAt,
                               Instant seenAt, Instant repliedAt, String photoUrl) {
}
