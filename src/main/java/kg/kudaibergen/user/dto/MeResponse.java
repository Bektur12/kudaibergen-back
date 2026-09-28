package kg.kudaibergen.user.dto;

import java.time.Instant;

import kg.kudaibergen.user.entity.AdminRole;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;

/** Профиль текущего пользователя. Счётчики гаража/запросов и магазин добавятся с их модулями. */
public record MeResponse(Long id, String phone, String name, UserRole role, Lang lang, AdminRole adminRole,
                         boolean onboarded, Instant createdAt) {

   public static MeResponse of(User user) {
      return new MeResponse(user.getId(), user.getPhone(), user.getName(), user.getRole(), user.getLang(),
            user.getAdminRole(), user.isOnboarded(), user.getCreatedAt());
   }
}
