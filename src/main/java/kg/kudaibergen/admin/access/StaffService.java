package kg.kudaibergen.admin.access;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.admin.access.StaffDtos.CreateStaffRequest;
import kg.kudaibergen.admin.access.StaffDtos.RolePermissionsDto;
import kg.kudaibergen.admin.access.StaffDtos.RolesDto;
import kg.kudaibergen.admin.access.StaffDtos.StaffDto;
import kg.kudaibergen.admin.access.StaffDtos.UpdateStaffRequest;
import kg.kudaibergen.auth.token.RefreshTokenService;
import kg.kudaibergen.auth.token.TokenAudience;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сотрудники. Новый сотрудник создаётся без пароля — задаёт его сам по SMS-коду. Нельзя отключить или понизить
 * себя и последнего активного SUPER_ADMIN. Смена роли или отключение закрывают его сессии админки.
 */
@Service
public class StaffService {

   private final AdminMemberRepository members;
   private final UserService users;
   private final AdminPermissions permissions;
   private final RefreshTokenService refreshTokens;
   private final NamedParameterJdbcTemplate jdbc;

   public StaffService(AdminMemberRepository members, UserService users, AdminPermissions permissions,
                       RefreshTokenService refreshTokens, NamedParameterJdbcTemplate jdbc) {
      this.members = members;
      this.users = users;
      this.permissions = permissions;
      this.refreshTokens = refreshTokens;
      this.jdbc = jdbc;
   }

   @Transactional(readOnly = true)
   public List<StaffDto> list() {
      return members.findAll().stream()
            .sorted(Comparator.comparing(AdminMember::isActive).reversed()
                  .thenComparing(AdminMember::getRole).thenComparing(AdminMember::getFullName))
            .map(this::dto).toList();
   }

   @Transactional(readOnly = true)
   public StaffDto get(Long userId) {
      return dto(member(userId));
   }

   @Transactional
   public StaffDto create(CreateStaffRequest request, Long adminId) {
      User user = users.findOrCreate(request.phone(), Lang.RU);
      if (members.existsById(user.getId())) {
         throw new ConflictException("STAFF_EXISTS", "Этот номер уже в сотрудниках — включите его в списке");
      }
      String title = request.title() == null || request.title().isBlank() ? request.role().defaultTitle()
            : request.title().strip();
      members.save(new AdminMember(user.getId(), request.role(), request.fullName().strip(), title, adminId));
      return get(user.getId());
   }

   @Transactional
   public StaffDto update(Long userId, UpdateStaffRequest request, Long adminId) {
      AdminMember member = member(userId);
      boolean downgrade = request.role() != null && request.role() != AdminRole.SUPER_ADMIN
            && member.getRole() == AdminRole.SUPER_ADMIN;
      boolean deactivate = Boolean.FALSE.equals(request.isActive()) && member.isActive();
      if (userId.equals(adminId) && (downgrade || deactivate || request.role() != null && request.role() != member.getRole())) {
         throw new ConflictException("STAFF_SELF", "Свою роль и доступ менять нельзя — попросите другого суперадмина");
      }
      if ((downgrade || deactivate) && member.getRole() == AdminRole.SUPER_ADMIN && member.isActive()
            && activeSuperAdmins() <= 1) {
         throw new ConflictException("LAST_SUPER_ADMIN", "Это последний суперадмин — сначала назначьте другого");
      }
      boolean accessChanged = deactivate || (request.role() != null && request.role() != member.getRole());
      if (request.role() != null) {
         member.setRole(request.role());
      }
      if (request.title() != null) {
         member.setTitle(request.title().strip());
      }
      if (request.fullName() != null) {
         member.setFullName(request.fullName().strip());
      }
      if (request.isActive() != null) {
         member.setActive(request.isActive());
      }
      if (accessChanged) {
         refreshTokens.revokeAll(userId, TokenAudience.ADMIN);
      }
      return dto(member);
   }

   @Transactional(readOnly = true)
   public RolesDto roles() {
      List<RolePermissionsDto> roles = Arrays.stream(AdminRole.values())
            .map(role -> new RolePermissionsDto(role, role.defaultTitle(), !role.hasAllPermissions(),
                  permissions.of(role).stream().sorted().toList()))
            .toList();
      return new RolesDto(roles, List.of(AdminPermission.values()));
   }

   /** Права роли целиком. SUPER_ADMIN не меняется (у него все права всегда). */
   @Transactional
   public RolesDto setRolePermissions(AdminRole role, Set<AdminPermission> granted) {
      if (role.hasAllPermissions()) {
         throw new BadRequestException("ROLE_FIXED", "У суперадмина все права — их не меняют");
      }
      jdbc.update("delete from admin_role_permissions where admin_role = :role", Map.of("role", role.name()));
      for (AdminPermission permission : granted) {
         jdbc.update("insert into admin_role_permissions (admin_role, permission) values (:role, :permission)",
               Map.of("role", role.name(), "permission", permission.name()));
      }
      return roles();
   }

   private long activeSuperAdmins() {
      return members.findAll().stream().filter(m -> m.isActive() && m.getRole() == AdminRole.SUPER_ADMIN).count();
   }

   private AdminMember member(Long userId) {
      return members.findById(userId).orElseThrow(() -> new NotFoundException("STAFF_NOT_FOUND", "Сотрудник не найден"));
   }

   private StaffDto dto(AdminMember member) {
      Map<String, Object> row = jdbc.queryForMap("""
            select u.phone, c.name as creator from users u left join users c on c.id = :creator where u.id = :id""",
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", member.getUserId())
                  .addValue("creator", member.getCreatedBy(), java.sql.Types.BIGINT));
      return new StaffDto(member.getUserId(), (String) row.get("phone"), member.getFullName(), member.getTitle(),
            member.getRole(), member.isActive(), member.hasPassword(), member.getLastLoginAt(),
            member.getCreatedAt(), (String) row.get("creator"));
   }
}
