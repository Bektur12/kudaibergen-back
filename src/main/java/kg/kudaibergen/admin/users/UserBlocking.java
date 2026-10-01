package kg.kudaibergen.admin.users;

import java.util.Map;

import kg.kudaibergen.admin.sanctions.SanctionTarget;
import kg.kudaibergen.admin.sanctions.SanctionType;
import kg.kudaibergen.admin.sanctions.Sanctions;
import kg.kudaibergen.auth.token.RefreshTokenService;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Блокировка пользователя целиком: вход закрыт (сессии гасятся), его магазин и профиль мастера блокируются —
 * запросы и заявки к ним не приходят. Снятие возвращает то, что было заблокировано вместе с аккаунтом.
 */
@Service
public class UserBlocking {

   /** Причина блокировки магазина или мастера «вместе с аккаунтом» — по ней их и разблокируют. */
   static final String OWNER_BLOCKED = "Аккаунт владельца заблокирован";

   private final UserRepository users;
   private final RefreshTokenService refreshTokens;
   private final NamedParameterJdbcTemplate jdbc;
   private final Sanctions sanctions;

   public UserBlocking(UserRepository users, RefreshTokenService refreshTokens, NamedParameterJdbcTemplate jdbc,
                       Sanctions sanctions) {
      this.users = users;
      this.refreshTokens = refreshTokens;
      this.jdbc = jdbc;
      this.sanctions = sanctions;
   }

   @Transactional
   public void block(Long userId, Long adminId, String reason) {
      User user = user(userId);
      if (user.isBlocked()) {
         throw new ConflictException("ALREADY_BLOCKED", "Пользователь уже заблокирован");
      }
      if (userId.equals(adminId)) {
         throw new ConflictException("SELF_BLOCK", "Нельзя заблокировать себя");
      }
      // иначе админ рынка с USERS_BLOCK закрыл бы вход суперадмину; доступ сотрудника снимают в «Сотрудниках»
      Boolean staff = jdbc.queryForObject(
            "select exists (select 1 from admin_members where user_id = :user and is_active)",
            Map.of("user", userId), Boolean.class);
      if (Boolean.TRUE.equals(staff)) {
         throw new ConflictException("STAFF_BLOCK",
               "Это сотрудник админки — сначала отключите его в разделе «Сотрудники»");
      }
      user.block(reason);
      refreshTokens.revokeAll(userId);
      MapSqlParameterSource params = new MapSqlParameterSource("user", userId)
            .addValue("reason", OWNER_BLOCKED + ": " + reason);
      jdbc.update("""
            update shops set status = 'BLOCKED', block_reason = left(:reason, 300)
            where owner_id = :user and status in ('ACTIVE', 'PENDING_VERIFICATION')""", params);
      jdbc.update("""
            update masters set status = 'BLOCKED', block_reason = left(:reason, 300)
            where owner_id = :user and status in ('ACTIVE', 'PENDING_VERIFICATION')""", params);
      sanctions.record(SanctionTarget.USER, userId, SanctionType.BLOCK, reason, adminId);
   }

   @Transactional
   public void unblock(Long userId, Long adminId) {
      User user = user(userId);
      if (!user.isBlocked()) {
         throw new ConflictException("NOT_BLOCKED", "Пользователь не заблокирован");
      }
      user.unblock();
      Map<String, Object> params = Map.of("user", userId, "prefix", OWNER_BLOCKED + "%");
      jdbc.update("""
            update shops set status = case when verified_at is null then 'PENDING_VERIFICATION' else 'ACTIVE' end,
                             block_reason = null
            where owner_id = :user and status = 'BLOCKED' and block_reason like :prefix""", params);
      jdbc.update("""
            update masters set status = 'ACTIVE', block_reason = null
            where owner_id = :user and status = 'BLOCKED' and block_reason like :prefix""", params);
      sanctions.record(SanctionTarget.USER, userId, SanctionType.UNBLOCK, null, adminId);
   }

   private User user(Long userId) {
      return users.findById(userId).orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Пользователь не найден"));
   }
}
