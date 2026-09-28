package kg.kudaibergen.user.dto;

import java.time.Instant;

import kg.kudaibergen.user.entity.AdminRole;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;

/**
 * Профиль текущего пользователя. avatarUrl = null — клиент рисует первую букву имени (19).
 * Счётчики гаража и запросов добавятся с их модулями.
 */
public record MeResponse(Long id, String phone, String name, String avatarUrl, UserRole role, Lang lang,
                         AdminRole adminRole, boolean onboarded, Instant createdAt) {

   public static MeResponse of(User user, String avatarUrl) {
      return new MeResponse(user.getId(), user.getPhone(), user.getName(), avatarUrl, user.getRole(),
            user.getLang(), user.getAdminRole(), user.isOnboarded(), user.getCreatedAt());
   }
}
