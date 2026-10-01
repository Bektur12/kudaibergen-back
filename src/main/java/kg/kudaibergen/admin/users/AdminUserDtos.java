package kg.kudaibergen.admin.users;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.admin.access.MaskedPhone;
import kg.kudaibergen.admin.sanctions.SanctionDto;
import org.springframework.lang.Nullable;

/** Пользователи (раздел сайдбара без макета). */
public final class AdminUserDtos {

   private AdminUserDtos() {
   }

   /** Фильтр списка: SELLER — есть бокс, MASTER — есть профиль мастера, BUYER — ни того ни другого. */
   public enum UserFilter { ALL, BUYER, SELLER, MASTER, BLOCKED }

   /**
    * Строка: roles — BUYER всегда, плюс SELLER (в боксе), MASTER (профиль мастера); mode — текущий режим
    * приложения. requests — запросов и заявок всего.
    */
   public record AdminUserRow(Long id, @MaskedPhone String phone, @Nullable String name, List<String> roles,
                              String mode, Instant registeredAt, @Nullable Instant lastSeenAt, long requests,
                              boolean blocked, @Nullable String blockedReason, boolean admin) {
   }

   public record UserCounts(long all, long buyers, long sellers, long masters, long blocked) {
   }

   public record UserShopDto(Long id, String name, String status, String memberRole) {
   }

   public record UserMasterDto(Long id, String name, String status) {
   }

   public record UserRequestDto(String kind, Long id, String text, String status, Instant createdAt) {
   }

   /** Жалоба в карточке: от пользователя или на его контент (отзывы, сообщения). */
   public record UserComplaintDto(Long id, String type, String reason, String status, Instant createdAt) {
   }

   public record AdminUserDetailDto(Long id, @MaskedPhone String phone, @Nullable String name, String lang,
                                    String mode, List<String> roles, Instant registeredAt,
                                    @Nullable Instant lastSeenAt, boolean blocked, @Nullable Instant blockedAt,
                                    @Nullable String blockedReason, @Nullable String adminRole, List<String> cars,
                                    @Nullable UserShopDto shop, @Nullable UserMasterDto master,
                                    List<UserRequestDto> recentRequests, long requestsTotal,
                                    List<UserComplaintDto> complaintsBy, List<UserComplaintDto> complaintsAbout,
                                    List<SanctionDto> sanctions) {
   }

   public record UserReasonRequest(@NotBlank(message = "Укажите причину") @Size(max = 300) String reason) {
   }

   public record UserMessageRequest(@NotBlank(message = "Напишите сообщение") @Size(max = 500) String text) {
   }
}
