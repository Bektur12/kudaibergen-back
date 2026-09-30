package kg.kudaibergen.master;

import java.util.Optional;

import kg.kudaibergen.user.UserMasters;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Профиль мастера пользователя для /me. */
@Component
public class MasterUserMasters implements UserMasters {

   private final MasterRepository masters;

   public MasterUserMasters(MasterRepository masters) {
      this.masters = masters;
   }

   @Override
   @Transactional(readOnly = true)
   public Optional<MasterRef> of(Long userId) {
      return masters.findByOwnerId(userId)
            .map(master -> new MasterRef(master.getId(), master.getName(), master.getStatus().name()));
   }
}
