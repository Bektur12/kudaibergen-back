package kg.kudaibergen.request.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.request.entity.RequestStatus;
import org.springframework.lang.Nullable;

/**
 * Запрос в ленте продавца (11) и в шторке ответа (12). Телефон покупателя не показывается;
 * buyerName = null — имя не заполнено, клиент пишет «Покупатель».
 * soldHere — покупатель закрыл запрос с этим боксом. myReply = null — ещё не ответили.
 * expiresAt — таймер «осталось 12 мин»; после него ответить нельзя (409 REQUEST_EXPIRED).
 */
public record IncomingRequestDto(Long id, String text, RequestCarDto car, @Nullable CategoryDto category,
                                 List<PhotoDto> photos, @Nullable String buyerName, RequestStatus status, boolean soldHere,
                                 Instant createdAt, Instant notifiedAt, Instant expiresAt, boolean seen,
                                 @Nullable ReplyDto myReply) {
}
