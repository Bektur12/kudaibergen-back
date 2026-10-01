package kg.kudaibergen.admin.shops;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.admin.access.MaskedPhone;
import kg.kudaibergen.admin.sanctions.SanctionDto;
import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.shop.dispute.DisputeStatus;
import kg.kudaibergen.shop.dispute.DisputeWinner;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.shop.entity.VerificationMethod;
import kg.kudaibergen.shop.entity.VerificationStatus;
import org.springframework.lang.Nullable;

/** DTO раздела «Продавцы» [A2]. */
public final class AdminShopDtos {

   private AdminShopDtos() {
   }

   /**
    * Строка списка. location — где стоит; pendingLocation — куда переезжает (проверяется оно).
    * submittedAt — когда подал на проверку (последняя заявка, иначе регистрация). warned — есть действующее
    * предупреждение. openDisputes — открытые споры с участием магазина.
    */
   public record AdminShopRow(Long id, String name, @Nullable String avatarUrl, ShopStatus status, boolean warned,
                              @MaskedPhone String ownerPhone, LocationDto location,
                              @Nullable LocationDto pendingLocation, TenantMatch tenantMatch, Instant submittedAt,
                              Instant createdAt, int openDisputes) {
   }

   public record ShopTabCounts(long all, long pending, long disputes, long blocked, long rejected) {
   }

   /** Арендатор контейнера по базе рынка; matches — совпадает ли телефон с кем-то из бокса (null — нечем сверить). */
   public record AdminTenantDto(@Nullable String name, @Nullable @MaskedPhone String phone, @Nullable Boolean matches) {
   }

   public record AdminShopOwnerDto(Long userId, @Nullable String name, @MaskedPhone String phone) {
   }

   public record AdminVerificationDto(Long id, VerificationMethod method, VerificationStatus status,
                                      @Nullable String reason, LocationDto location, Instant createdAt,
                                      @Nullable Instant decidedAt) {
   }

   /**
    * Правая панель [A2]. statusReason — причина блокировки или отказа. tenant / tenantMatch — по контейнеру,
    * который проверяется. complaints90d / warnings90d — для решения о санкциях.
    */
   public record AdminShopDetailDto(Long id, String publicId, String name, @Nullable String avatarUrl,
                                    ShopStatus status, @Nullable String statusReason, boolean warned,
                                    AdminShopOwnerDto owner, @Nullable @MaskedPhone String shopPhone,
                                    LocationDto location, @Nullable LocationDto pendingLocation,
                                    AdminTenantDto tenant, TenantMatch tenantMatch, List<String> brands,
                                    List<String> categories, LocalTime openFrom, LocalTime openTo,
                                    Set<DayOfWeek> workDays, boolean open, java.math.BigDecimal rating,
                                    int reviewsCount, long activeParts, long staffCount, Instant createdAt,
                                    @Nullable Instant verifiedAt, List<AdminVerificationDto> verifications,
                                    List<AdminDisputeDto> openDisputes, long complaints90d, long warnings90d,
                                    List<SanctionDto> sanctions) {
   }

   /** Сторона спора. shopId / shopName — null, если у заявителя магазина нет. */
   public record AdminDisputePartyDto(@Nullable Long userId, @Nullable String name,
                                      @Nullable @MaskedPhone String phone, @Nullable Long shopId,
                                      @Nullable String shopName) {
   }

   /** Чей телефон совпал с телефоном арендатора по базе рынка. UNKNOWN — телефона арендатора нет. */
   public enum TenantSide { CLAIMANT, CURRENT, NONE, UNKNOWN }

   /**
    * Спор за контейнер. tenant — арендатор контейнера по базе рынка (matches всегда null), tenantSide — на чьей
    * он стороне по телефону: подсказка для решения.
    */
   public record AdminDisputeDto(Long id, DisputeStatus status, LocationDto location, AdminTenantDto tenant,
                                 TenantSide tenantSide,
                                 AdminDisputePartyDto claimant, AdminDisputePartyDto current, @Nullable String text,
                                 @Nullable DisputeWinner winner, @Nullable String resolution, Instant createdAt,
                                 @Nullable Instant resolvedAt) {
   }

   public record DisputeCounts(long open, long resolved) {
   }

   // ─────────────── запросы ───────────────

   public record ShopReasonRequest(@NotBlank(message = "Укажите причину") @Size(max = 300) String reason) {
   }

   public record AdminMessageRequest(@NotBlank(message = "Напишите сообщение") @Size(max = 500) String text) {
   }

   public record ResolveDisputeRequest(@NotNull(message = "Кому достаётся контейнер") DisputeWinner winner,
                                       @Size(max = 1000) String comment) {
   }

   /** Сколько человек получили сообщение (у кого есть устройство — получат пуш). */
   public record AdminMessageSentDto(int recipients) {
   }
}
