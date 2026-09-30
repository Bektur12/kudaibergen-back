package kg.kudaibergen.admin.access;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.admin.access.dto.AdminMeDto;
import kg.kudaibergen.common.error.UnauthorizedException;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/me")
@Tag(name = "Админка: вход")
public class AdminMeController {

   private final AdminMemberRepository members;
   private final UserRepository users;
   private final AdminProfiles profiles;

   public AdminMeController(AdminMemberRepository members, UserRepository users, AdminProfiles profiles) {
      this.members = members;
      this.users = users;
      this.profiles = profiles;
   }

   @GetMapping
   @PreAuthorize("hasRole('ADMIN')")
   @Transactional(readOnly = true)
   @Operation(summary = "Текущий сотрудник", description = "Роль, права и бейджи сайдбара: продавцы и мастера на "
         + "проверке, новые жалобы. Бейдж null — раздел сотруднику недоступен")
   public AdminMeDto me(@AuthenticationPrincipal AuthPrincipal principal) {
      AdminMember member = members.findById(principal.userId()).orElseThrow(AdminMeController::gone);
      return profiles.of(member, users.findById(principal.userId()).orElseThrow(AdminMeController::gone),
            principal.permissions());
   }

   private static UnauthorizedException gone() {
      return new UnauthorizedException("UNAUTHORIZED", "Требуется авторизация");
   }
}
