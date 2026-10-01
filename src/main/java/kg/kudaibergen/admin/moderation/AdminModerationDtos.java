package kg.kudaibergen.admin.moderation;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.admin.access.MaskedPhone;
import kg.kudaibergen.admin.sanctions.SanctionTarget;
import kg.kudaibergen.complaint.ComplaintOutcome;
import kg.kudaibergen.complaint.ComplaintReason;
import kg.kudaibergen.complaint.ComplaintStatus;
import kg.kudaibergen.complaint.ComplaintType;
import kg.kudaibergen.media.PhotoDto;
import org.springframework.lang.Nullable;

/** Модерация [A4]. */
public final class AdminModerationDtos {

   private AdminModerationDtos() {
   }

   /** Действие по жалобе: скрыть контент, предупредить, заблокировать, жалоба необоснованна. */
   public enum ModerationAction { REMOVE_CONTENT, WARN_SELLER, BLOCK_SHOP, UNFOUNDED }

   /** Кто подал: роль — режим, в котором был (BUYER / SELLER / MASTER). */
   public record ReporterDto(@Nullable Long userId, @Nullable String name, @Nullable @MaskedPhone String phone,
                             @Nullable String role) {
   }

   /**
    * Ответственный за объект жалобы — тот, кого предупреждают или блокируют: магазин, мастер или пользователь
    * (автор отзыва, собеседник в чате). location — «Ряд 14 · 12» у магазина, адрес у мастера.
    */
   public record PartyDto(SanctionTarget kind, Long id, String name, @Nullable @MaskedPhone String phone,
                          @Nullable String status, @Nullable String location) {
   }

   /** Строка ленты: subjectTitle — что за объект («Колодки Toyota Camry 50», «Отзыв ★1», текст сообщения). */
   public record ComplaintRow(Long id, ComplaintType type, ComplaintReason reason, ComplaintStatus status,
                              @Nullable ComplaintOutcome outcome, String subjectTitle, @Nullable String partyName,
                              ReporterDto reporter, @Nullable String text, Instant createdAt) {
   }

   public record ComplaintCounts(long open, long resolved, long rejected) {
   }

   /**
    * Объект жалобы для карточки: заголовок, текст, фото, цена, звёзды, машины (у запчасти), место, скрыт ли уже.
    * Поля, которых у типа нет, — null / пусто.
    */
   public record ComplaintSubjectDto(ComplaintType type, Long id, String title, @Nullable String text,
                                     List<PhotoDto> photos, @Nullable Integer price, @Nullable Integer stars,
                                     List<String> fitments, @Nullable String status, boolean hidden,
                                     @Nullable String hiddenReason, @Nullable Instant createdAt) {
   }

   /** Статистика ответственного за 90 дней: жалоб, предупреждений, скрытого контента. */
   public record PartyStatsDto(long complaints90d, long warnings90d, long removed90d) {
   }

   /** Связанный запрос или заявка, из которых пришла жалоба. */
   public record RelatedRequestDto(String kind, Long id, String text, String status, Instant createdAt) {
   }

   public record ComplaintDetailDto(Long id, ComplaintType type, ComplaintReason reason, ComplaintStatus status,
                                    @Nullable ComplaintOutcome outcome, @Nullable String text, Instant createdAt,
                                    ReporterDto reporter, @Nullable ComplaintSubjectDto subject,
                                    @Nullable PartyDto party, @Nullable PartyStatsDto partyStats,
                                    @Nullable RelatedRequestDto related, long sameTargetOpen,
                                    @Nullable String resolution, @Nullable Long resolvedBy,
                                    @Nullable Instant resolvedAt) {
   }

   public record ResolveComplaintRequest(@NotNull ModerationAction action, @Size(max = 1000) String comment) {
   }

   /** resolvedTogether — сколько ещё открытых жалоб на тот же объект закрыто этим решением. */
   public record ResolvedComplaintDto(ComplaintDetailDto complaint, int resolvedTogether) {
   }
}
