package kg.kudaibergen.user;

import kg.kudaibergen.common.security.AdminGrants;
import kg.kudaibergen.common.security.AdminGrants.AdminGrant;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.user.dto.MeResponse;
import kg.kudaibergen.user.entity.User;
import org.springframework.stereotype.Component;

/** Сборка /me: аватар, бокс, профиль мастера и роль в админке. Тот же объект приходит в ответе входа. */
@Component
public class MeView {

   private final MediaService media;
   private final UserShops shops;
   private final UserMasters masters;
   private final AdminGrants admins;

   public MeView(MediaService media, UserShops shops, UserMasters masters, AdminGrants admins) {
      this.media = media;
      this.shops = shops;
      this.masters = masters;
      this.admins = admins;
   }

   public MeResponse of(User user) {
      return MeResponse.of(user, media.thumbUrl(user.getAvatarMediaId()), shops.of(user.getId()).orElse(null),
            masters.of(user.getId()).orElse(null), admins.active(user.getId()).map(AdminGrant::role).orElse(null));
   }
}
