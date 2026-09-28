package kg.kudaibergen.request.dto;

import java.time.Instant;

import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.request.entity.RequestStatus;
import kg.kudaibergen.request.entity.RequestTarget;

/**
 * Запрос покупателя (07, 09, 20). recipientsCount — скольким боксам ушёл, seenCount — сколько открыли,
 * haveCount — «3 продавца ответили «есть»».
 */
public record RequestDetailDto(Long id, String text, RequestCarDto car, CategoryDto category, RequestTarget target,
                               Long targetRowId, Long targetShopId, RequestStatus status, RequestState state,
                               int recipientsCount, long seenCount, int haveCount, Long closedWithShopId,
                               Instant createdAt, Instant closedAt) {
}
