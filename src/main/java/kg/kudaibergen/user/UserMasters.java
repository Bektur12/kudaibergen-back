package kg.kudaibergen.user;

import java.util.Optional;

import io.swagger.v3.oas.annotations.media.Schema;

/** Профиль мастера пользователя для /me: куда вести в режиме мастера — регистрация (38) или заявки (39). */
public interface UserMasters {

   Optional<MasterRef> of(Long userId);

   record MasterRef(Long id, String name,
                    @Schema(allowableValues = {"PENDING_VERIFICATION", "ACTIVE", "BLOCKED"}) String status) {
   }
}
