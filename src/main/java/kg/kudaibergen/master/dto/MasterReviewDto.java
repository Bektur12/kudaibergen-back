package kg.kudaibergen.master.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.request.dto.ReviewTagDto;
import org.springframework.lang.Nullable;

/** Отзыв о мастере: звёзды, теги, имя клиента (null — не заполнено или аккаунт удалён). */
public record MasterReviewDto(Long id, int stars, List<ReviewTagDto> tags, @Nullable String buyerName, Instant createdAt) {
}
