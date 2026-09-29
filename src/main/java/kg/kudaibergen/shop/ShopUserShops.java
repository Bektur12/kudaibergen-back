package kg.kudaibergen.shop;

import java.util.Optional;

import kg.kudaibergen.user.UserShops;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Бокс пользователя для /me: владелец или сотрудник. */
@Component
public class ShopUserShops implements UserShops {

   private final ShopAccess access;

   public ShopUserShops(ShopAccess access) {
      this.access = access;
   }

   @Override
   @Transactional(readOnly = true)
   public Optional<ShopRef> of(Long userId) {
      return access.membership(userId).map(membership -> new ShopRef(membership.shop().getId(),
            membership.shop().getName(), membership.shop().getStatus().name(), membership.member().getRole().name()));
   }
}
