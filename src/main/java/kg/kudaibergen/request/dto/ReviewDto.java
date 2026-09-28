package kg.kudaibergen.request.dto;

import java.time.Instant;
import java.util.List;

/**
 * Отзыв (вкладка «Отзывы» на 30, список на 21). buyerName = null — покупатель удалил аккаунт или не указал
 * имя: клиент пишет «Покупатель». reply — ответ продавца, один на отзыв.
 */
public record ReviewDto(Long id, int stars, List<ReviewTagDto> tags, String buyerName, String reply,
                        Instant repliedAt, Instant createdAt) {
}
