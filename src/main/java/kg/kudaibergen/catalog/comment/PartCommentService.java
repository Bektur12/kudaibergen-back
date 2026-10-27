package kg.kudaibergen.catalog.comment;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import kg.kudaibergen.catalog.PartRepository;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.ratelimit.RateLimiter;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.entity.ShopMember;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Комментарии под видео товара (лента «как рилсы»). Пишут вошедшие; удаляет автор или продавец бокса;
 * на неприличное — жалоба PART_COMMENT, скрывает админ. Продавцу бокса — пуш о новом комментарии.
 */
@Service
public class PartCommentService {

   static final int HOURLY_LIMIT = 30;

   private final PartCommentRepository comments;
   private final PartRepository parts;
   private final ShopMemberRepository members;
   private final RateLimiter rateLimiter;
   private final ApplicationEventPublisher events;

   public PartCommentService(PartCommentRepository comments, PartRepository parts, ShopMemberRepository members,
                             RateLimiter rateLimiter, ApplicationEventPublisher events) {
      this.comments = comments;
      this.parts = parts;
      this.members = members;
      this.rateLimiter = rateLimiter;
      this.events = events;
   }

   /** Событие после коммита — пуш продавцу. */
   public record CommentAdded(Long partId, Long commentId, Long authorId) {
   }

   @Transactional(readOnly = true)
   public PartCommentDto.PartCommentPageDto page(Long viewerId, Long partId, String cursor, Integer limit) {
      Part part = visiblePart(partId);
      int size = CursorPage.limit(limit);
      List<PartCommentRepository.CommentRow> rows = comments.page(partId, beforeId(cursor), size + 1);
      Set<Long> staff = staff(part);
      boolean viewerIsSeller = viewerId != null && staff.contains(viewerId);
      CursorPage<PartCommentDto> page = CursorPage.of(rows, size, row -> row.id().toString(),
            row -> new PartCommentDto(row.id(), row.text(), row.userName(), staff.contains(row.userId()),
                  row.userId().equals(viewerId), row.userId().equals(viewerId) || viewerIsSeller, row.createdAt()));
      return new PartCommentDto.PartCommentPageDto(page.items(), page.nextCursor(), comments.count(partId));
   }

   @Transactional
   public PartCommentDto add(Long userId, Long partId, PartCommentInput input) {
      Part part = visiblePart(partId);
      String text = input.text().strip();
      if (text.isEmpty()) {
         throw new BadRequestException("TEXT_REQUIRED", "Напишите комментарий");
      }
      rateLimiter.hit("part-comment:" + userId, HOURLY_LIMIT, Duration.ofHours(1), "COMMENT_LIMIT",
            "Слишком много комментариев — попробуйте позже");
      Long id = comments.add(partId, userId, text);
      boolean fromSeller = staff(part).contains(userId);
      events.publishEvent(new CommentAdded(partId, id, userId));
      PartCommentRepository.CommentRow row = comments.page(partId, id + 1, 1).get(0);
      return new PartCommentDto(id, row.text(), row.userName(), fromSeller, true, true, row.createdAt());
   }

   /** Удалить: свой — автор, любой под своим товаром — продавец бокса. */
   @Transactional
   public void delete(Long userId, Long partId, Long commentId) {
      Part part = parts.findById(partId).orElseThrow(PartCommentService::notFound);
      Long author = comments.authorOf(partId, commentId);
      if (author == null) {
         throw new NotFoundException("COMMENT_NOT_FOUND", "Комментарий уже удалён");
      }
      if (!author.equals(userId) && !staff(part).contains(userId)) {
         throw new ForbiddenException("NOT_YOUR_COMMENT", "Удалить можно только свой комментарий");
      }
      comments.delete(commentId);
   }

   private Part visiblePart(Long partId) {
      Part part = parts.findById(partId).orElseThrow(PartCommentService::notFound);
      if (!part.isActive() || part.isHiddenByAdmin()) {
         throw notFound();
      }
      return part;
   }

   private Set<Long> staff(Part part) {
      return members.findByShopIdOrderByCreatedAtAsc(part.getShopId()).stream().map(ShopMember::getUserId)
            .collect(Collectors.toSet());
   }

   private static Long beforeId(String cursor) {
      if (cursor == null || cursor.isBlank()) {
         return null;
      }
      try {
         return Long.parseLong(CursorPage.decode(cursor));
      } catch (NumberFormatException e) {
         throw new BadRequestException("BAD_CURSOR", "Некорректный курсор");
      }
   }

   private static NotFoundException notFound() {
      return new NotFoundException("PART_NOT_FOUND", "Товар не найден");
   }
}
