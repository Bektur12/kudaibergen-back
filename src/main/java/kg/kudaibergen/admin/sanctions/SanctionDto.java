package kg.kudaibergen.admin.sanctions;

import java.time.Instant;

import org.springframework.lang.Nullable;

/** Строка истории санкций в карточке: activeUntil — до когда действует предупреждение. */
public record SanctionDto(Long id, SanctionType type, @Nullable String reason, @Nullable Long adminId,
                          @Nullable Instant activeUntil, Instant createdAt) {

   public static SanctionDto of(Sanction sanction) {
      return new SanctionDto(sanction.getId(), sanction.getType(), sanction.getReason(), sanction.getAdminId(),
            sanction.getActiveUntil(), sanction.getCreatedAt());
   }
}
