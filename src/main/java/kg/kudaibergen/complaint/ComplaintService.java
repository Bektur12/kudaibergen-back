package kg.kudaibergen.complaint;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.ratelimit.RateLimiter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Жалобы из приложений (покупатель, продавец, мастер). Решает администрация — раздел «Модерация» [A4].
 * Повторная открытая жалоба того же автора на то же не плодит дублей.
 */
@Service
public class ComplaintService {

   static final int DAILY_LIMIT = 20;

   /** Типы, на которые можно пожаловаться из приложения, и где искать объект. */
   static final Map<ComplaintType, String> TARGETS = Map.of(
         ComplaintType.SHOP, "select count(*) from shops where id = :id",
         ComplaintType.PART, "select count(*) from parts where id = :id",
         ComplaintType.SHOP_PHOTO, "select count(*) from shop_photos where media_id = :id",
         ComplaintType.REVIEW, "select count(*) from reviews where id = :id",
         ComplaintType.MASTER_REVIEW, "select count(*) from master_reviews where id = :id",
         ComplaintType.CHAT, "select count(*) from chats where id = :id",
         ComplaintType.CHAT_MESSAGE, "select count(*) from messages where id = :id",
         ComplaintType.MASTER, "select count(*) from masters where id = :id",
         ComplaintType.SERVICE_OFFER, "select count(*) from service_offers where id = :id");

   private final ComplaintRepository complaints;
   private final NamedParameterJdbcTemplate jdbc;
   private final RateLimiter rateLimiter;

   public ComplaintService(ComplaintRepository complaints, NamedParameterJdbcTemplate jdbc, RateLimiter rateLimiter) {
      this.complaints = complaints;
      this.jdbc = jdbc;
      this.rateLimiter = rateLimiter;
   }

   /** Жалоба из меню чата (как раньше). */
   @Transactional
   public ComplaintDto create(Long authorId, ComplaintType type, Long targetId, String text) {
      return create(authorId, type, targetId, ComplaintReason.OTHER, text, null, null);
   }

   @Transactional
   public ComplaintDto create(Long authorId, ComplaintType type, Long targetId, ComplaintReason reason, String text,
                              Long relatedRequestId, Long relatedServiceRequestId) {
      String sql = TARGETS.get(type);
      if (sql == null) {
         throw new BadRequestException("BAD_COMPLAINT_TYPE", "На это пожаловаться нельзя");
      }
      Long found = jdbc.queryForObject(sql, Map.of("id", targetId), Long.class);
      if (found == null || found == 0) {
         throw new NotFoundException("COMPLAINT_TARGET_NOT_FOUND", "Не нашли то, на что жалоба");
      }
      return complaints.findFirstByAuthorIdAndTypeAndTargetIdAndStatus(authorId, type, targetId, ComplaintStatus.OPEN)
            .map(ComplaintDto::of)
            .orElseGet(() -> {
               rateLimiter.hit("complaints:" + authorId, DAILY_LIMIT, Duration.ofDays(1), "COMPLAINTS_RATE_LIMITED",
                     "Слишком много жалоб за сутки, попробуйте завтра");
               String role = jdbc.queryForList("select role from users where id = :id", Map.of("id", authorId),
                     String.class).stream().findFirst().orElse(null);
               return ComplaintDto.of(complaints.save(new Complaint(authorId, type, targetId,
                     text == null || text.isBlank() ? null : text.strip(), reason, role, relatedRequestId,
                     relatedServiceRequestId)));
            });
   }

   static Set<ComplaintType> publicTypes() {
      return TARGETS.keySet();
   }
}
