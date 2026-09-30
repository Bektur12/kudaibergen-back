package kg.kudaibergen.user;

import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.user.dto.MeResponse;
import kg.kudaibergen.user.entity.User;
import org.springframework.stereotype.Component;

/** Сборка /me: аватар, бокс и профиль мастера пользователя. Тот же объект приходит в ответе входа. */
@Component
public class MeView {

   private final MediaService media;
   private final UserShops shops;
   private final UserMasters masters;

   public MeView(MediaService media, UserShops shops, UserMasters masters) {
      this.media = media;
      this.shops = shops;
      this.masters = masters;
   }

   public MeResponse of(User user) {
      return MeResponse.of(user, media.thumbUrl(user.getAvatarMediaId()), shops.of(user.getId()).orElse(null),
            masters.of(user.getId()).orElse(null));
   }
}
