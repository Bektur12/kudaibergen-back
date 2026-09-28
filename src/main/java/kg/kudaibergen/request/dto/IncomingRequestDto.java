package kg.kudaibergen.request.dto;

import java.time.Instant;

import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.request.entity.RequestStatus;

/**
 * Запрос в ленте продавца (11) и в шторке ответа (12). Телефон покупателя не показывается;
 * buyerName = null — имя не заполнено, клиент пишет «Покупатель».
 * soldHere — покупатель закрыл запрос с этим боксом. myReply = null — ещё не ответили.
 */
public record IncomingRequestDto(Long id, String text, RequestCarDto car, CategoryDto category, String buyerName,
                                 RequestStatus status, boolean soldHere, Instant createdAt, Instant notifiedAt,
                                 boolean seen, ReplyDto myReply) {
}
