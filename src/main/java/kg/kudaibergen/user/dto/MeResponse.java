package kg.kudaibergen.user.dto;

import java.time.Instant;

import kg.kudaibergen.user.UserMasters;
import kg.kudaibergen.user.UserShops;
import kg.kudaibergen.user.entity.AdminRole;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;
import org.springframework.lang.Nullable;

/**
 * Профиль текущего пользователя. avatarUrl = null — клиент рисует первую букву имени (19).
 * hasShop / shop — бокс, где пользователь владелец или сотрудник: нет — продавца ведут на регистрацию (10а).
 * hasMaster / master — профиль мастера: нет — в режиме мастера ведут на регистрацию (38).
 */
public record MeResponse(Long id, String phone, @Nullable String name, @Nullable String avatarUrl, UserRole role, Lang lang,
                         @Nullable AdminRole adminRole, boolean onboarded, boolean hasShop, @Nullable UserShops.ShopRef shop,
                         boolean hasMaster, @Nullable UserMasters.MasterRef master, Instant createdAt) {

   public static MeResponse of(User user, String avatarUrl, UserShops.ShopRef shop, UserMasters.MasterRef master) {
      return new MeResponse(user.getId(), user.getPhone(), user.getName(), avatarUrl, user.getRole(),
            user.getLang(), user.getAdminRole(), user.isOnboarded(), shop != null, shop, master != null, master,
            user.getCreatedAt());
   }
}
