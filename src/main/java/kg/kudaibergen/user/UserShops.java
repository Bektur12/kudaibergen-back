package kg.kudaibergen.user;

import java.util.Optional;

/**
 * Бокс пользователя для /me: куда вести продавца после входа — регистрация (10а) или запросы (11).
 * Реализует модуль shops, чтобы модуль user не зависел от него.
 */
public interface UserShops {

   Optional<ShopRef> of(Long userId);

   /** status — PENDING_VERIFICATION / ACTIVE / BLOCKED, role — OWNER / STAFF. */
   record ShopRef(Long id, String name, String status, String role) {
   }
}
