package kg.kudaibergen.admin.access;

import kg.kudaibergen.common.config.AdminProperties;
import kg.kudaibergen.common.security.AdminRole;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Первый SUPER_ADMIN: при старте, если активного суперадмина нет и задан ADMIN_BOOTSTRAP_PHONE,
 * этот номер становится суперадмином (пользователь создаётся, если его нет). Пароль он задаёт сам:
 * POST /api/v1/admin/auth/password/code → /password.
 */
@Component
public class AdminBootstrap {

   private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

   private final AdminProperties config;
   private final AdminMemberRepository members;
   private final UserService users;

   public AdminBootstrap(AdminProperties config, AdminMemberRepository members, UserService users) {
      this.config = config;
      this.members = members;
      this.users = users;
   }

   @EventListener(ApplicationReadyEvent.class)
   @Transactional
   public void ensureSuperAdmin() {
      String phone = config.bootstrapPhone();
      if (phone == null || phone.isBlank() || members.existsByRoleAndActiveTrue(AdminRole.SUPER_ADMIN)) {
         return;
      }
      if (!phone.strip().matches("\\+996\\d{9}")) {
         log.error("ADMIN_BOOTSTRAP_PHONE={} — не номер +996XXXXXXXXX, суперадмин не создан", phone);
         return;
      }
      User user = users.findOrCreate(phone.strip(), Lang.RU);
      AdminMember member = members.findById(user.getId()).orElse(null);
      if (member == null) {
         String name = config.bootstrapName() == null || config.bootstrapName().isBlank()
               ? AdminRole.SUPER_ADMIN.defaultTitle() : config.bootstrapName().strip();
         members.save(new AdminMember(user.getId(), AdminRole.SUPER_ADMIN, name,
               AdminRole.SUPER_ADMIN.defaultTitle(), null));
      } else {
         member.setRole(AdminRole.SUPER_ADMIN);
         member.setActive(true);
      }
      log.warn("Админка: {} назначен суперадмином (ADMIN_BOOTSTRAP_PHONE). Пароль задаётся по SMS: "
            + "POST /api/v1/admin/auth/password/code", phone);
   }
}
