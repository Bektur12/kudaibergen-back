package kg.kudaibergen.admin.access;

import java.util.Optional;

import kg.kudaibergen.common.security.AdminGrants;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Действующие права сотрудника: активен, пользователь не заблокирован и не удаляется. */
@Component
public class AdminAccess implements AdminGrants {

   private final AdminMemberRepository members;
   private final UserRepository users;
   private final AdminPermissions permissions;

   public AdminAccess(AdminMemberRepository members, UserRepository users, AdminPermissions permissions) {
      this.members = members;
      this.users = users;
      this.permissions = permissions;
   }

   @Override
   @Transactional(readOnly = true)
   public Optional<AdminGrant> active(Long userId) {
      return members.findById(userId)
            .filter(AdminMember::isActive)
            .filter(member -> users.findById(userId).filter(AdminAccess::canWork).isPresent())
            .map(member -> new AdminGrant(member.getRole(), permissions.of(member.getRole())));
   }

   static boolean canWork(User user) {
      return !user.isBlocked() && user.getDeletionRequestedAt() == null;
   }
}
