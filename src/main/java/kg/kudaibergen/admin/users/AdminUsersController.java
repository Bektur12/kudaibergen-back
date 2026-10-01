package kg.kudaibergen.admin.users;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminMessageSentDto;
import kg.kudaibergen.admin.users.AdminUserDtos.AdminUserDetailDto;
import kg.kudaibergen.admin.users.AdminUserDtos.AdminUserRow;
import kg.kudaibergen.admin.users.AdminUserDtos.UserCounts;
import kg.kudaibergen.admin.users.AdminUserDtos.UserFilter;
import kg.kudaibergen.admin.users.AdminUserDtos.UserMessageRequest;
import kg.kudaibergen.admin.users.AdminUserDtos.UserReasonRequest;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Пользователи. */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Админка: пользователи")
public class AdminUsersController {

   private final AdminUsersService users;

   public AdminUsersController(AdminUsersService users) {
      this.users = users;
   }

   @GetMapping
   @PreAuthorize("hasAuthority('USERS_VIEW')")
   @Operation(summary = "Пользователи", description = "role: BUYER / SELLER / MASTER / BLOCKED; q — имя или цифры телефона")
   public AdminPage<AdminUserRow, UserCounts> list(@RequestParam(defaultValue = "ALL") UserFilter role,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(required = false) String cursor,
                                                   @RequestParam(required = false) Integer limit) {
      return users.list(role, q, cursor, limit);
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('USERS_VIEW')")
   @Operation(summary = "Карточка: гараж, бокс / мастер, последние запросы, жалобы от него и на него, санкции")
   public AdminUserDetailDto detail(@PathVariable Long id) {
      return users.detail(id);
   }

   @PostMapping("/{id}/block")
   @PreAuthorize("hasAuthority('USERS_BLOCK')")
   @Audited(action = "USER_BLOCK", entity = "USER", id = "#id", comment = "#request.reason")
   @Operation(summary = "Заблокировать", description = "Сессии закрываются, вход запрещён; его бокс и профиль мастера "
         + "блокируются вместе с ним")
   @ApiResponse(responseCode = "409", description = "ALREADY_BLOCKED, SELF_BLOCK, STAFF_BLOCK — активный сотрудник админки")
   public AdminUserDetailDto block(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                                   @Valid @RequestBody UserReasonRequest request) {
      AuditTrail.before(users.detail(id));
      users.block(id, admin.userId(), request.reason());
      return users.detail(id);
   }

   @PostMapping("/{id}/unblock")
   @PreAuthorize("hasAuthority('USERS_BLOCK')")
   @Audited(action = "USER_UNBLOCK", entity = "USER", id = "#id")
   @Operation(summary = "Снять блокировку", description = "Бокс и профиль мастера, заблокированные вместе с аккаунтом, "
         + "возвращаются")
   @ApiResponse(responseCode = "409", description = "NOT_BLOCKED")
   public AdminUserDetailDto unblock(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin) {
      AuditTrail.before(users.detail(id));
      users.unblock(id, admin.userId());
      return users.detail(id);
   }

   @PostMapping("/{id}/message")
   @PreAuthorize("hasAuthority('MESSAGE_USERS')")
   @Audited(action = "USER_MESSAGE", entity = "USER", id = "#id", comment = "#request.text")
   @Operation(summary = "Написать", description = "Пуш ADMIN_MESSAGE. Ответ — как у магазина и мастера: {recipients}")
   public AdminMessageSentDto message(@PathVariable Long id, @Valid @RequestBody UserMessageRequest request) {
      users.message(id, request.text());
      return new AdminMessageSentDto(1);
   }
}
