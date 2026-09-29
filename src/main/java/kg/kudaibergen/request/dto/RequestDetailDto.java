package kg.kudaibergen.request.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.request.entity.RequestDuration;
import kg.kudaibergen.request.entity.RequestStatus;
import kg.kudaibergen.request.entity.RequestTarget;
import org.springframework.lang.Nullable;

/**
 * Запрос покупателя (07, 09, 20, 32). recipientsCount — скольким боксам ушёл, seenCount — сколько открыли,
 * haveCount — «3 продавца ответили «есть»». Живёт до expiresAt; canExtend — можно ли ещё продлить
 * (не больше 3 раз, extendedTimes — сколько уже).
 */
public record RequestDetailDto(Long id, String text, RequestCarDto car, @Nullable CategoryDto category, List<PhotoDto> photos,
                               RequestTarget target, List<Long> targetRowIds, List<Long> targetContainerIds,
                               RequestStatus status, RequestState state, RequestDuration duration,
                               Instant expiresAt, int extendedTimes, boolean canExtend,
                               int recipientsCount, long seenCount, int haveCount, @Nullable Long closedWithShopId,
                               Instant createdAt, @Nullable Instant closedAt) {
}
