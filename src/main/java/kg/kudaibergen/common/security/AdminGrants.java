package kg.kudaibergen.common.security;

import java.util.Optional;
import java.util.Set;

/**
 * Текущие права сотрудника. Фильтр токенов спрашивает их на каждый запрос к админке, поэтому
 * отключение сотрудника или смена его роли действуют сразу, не дожидаясь конца жизни токена.
 * Реализация — в admin.access.
 */
public interface AdminGrants {

   /** Пусто — сотрудника нет, он отключён или пользователь заблокирован. */
   Optional<AdminGrant> active(Long userId);

   record AdminGrant(AdminRole role, Set<AdminPermission> permissions) {
   }
}
