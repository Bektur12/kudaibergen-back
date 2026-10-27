package kg.kudaibergen.catalog.comment;

import java.time.Instant;
import java.util.List;

import org.springframework.lang.Nullable;

/**
 * Комментарий под видео товара. authorName — имя (null — «Покупатель»), fromSeller — пишет продавец этого бокса
 * (плашка «Продавец»), canDelete — свой комментарий или зритель — продавец бокса.
 */
public record PartCommentDto(Long id, String text, @Nullable String authorName, boolean fromSeller, boolean mine,
                             boolean canDelete, Instant createdAt) {

   /** Страница: новые сверху; total — сколько всего у товара. */
   public record PartCommentPageDto(List<PartCommentDto> items, @Nullable String nextCursor, long total) {
   }
}
