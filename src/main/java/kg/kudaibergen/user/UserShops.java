package kg.kudaibergen.user;

import java.util.Optional;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Бокс пользователя для /me: куда вести продавца после входа — регистрация (10а) или запросы (11).
 * Реализует модуль shops, чтобы модуль user не зависел от него.
 */
public interface UserShops {

   Optional<ShopRef> of(Long userId);

   /**
    * status и role — строками, чтобы модуль user не зависел от енамов shop; в OpenAPI это енамы
    * (значения ShopStatus и MemberRole).
    */
   record ShopRef(Long id, String name,
                  @Schema(allowableValues = {"PENDING_VERIFICATION", "ACTIVE", "BLOCKED"}) String status,
                  @Schema(allowableValues = {"OWNER", "STAFF"}) String role) {
   }
}
