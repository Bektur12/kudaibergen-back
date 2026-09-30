package kg.kudaibergen.admin.access;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import kg.kudaibergen.admin.access.dto.AdminMeDto;
import kg.kudaibergen.admin.access.dto.AdminMeDto.AdminBadgesDto;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.user.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Профиль сотрудника для шапки и сайдбара, с бейджами по разделам, которые ему доступны. */
@Component
public class AdminProfiles {

   private final JdbcTemplate jdbc;

   public AdminProfiles(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   public AdminMeDto of(AdminMember member, User user, Set<AdminPermission> permissions) {
      List<AdminPermission> sorted = permissions.stream().sorted(Comparator.comparing(Enum::ordinal)).toList();
      return new AdminMeDto(user.getId(), user.getPhone(), member.getFullName(), member.getTitle(), member.getRole(),
            sorted, badges(permissions), member.getLastLoginAt());
   }

   AdminBadgesDto badges(Set<AdminPermission> permissions) {
      return new AdminBadgesDto(
            permissions.contains(AdminPermission.SELLERS_VIEW) ? count("""
                  select count(*) from shops
                  where status = 'PENDING_VERIFICATION' or pending_container_id is not null""") : null,
            permissions.contains(AdminPermission.MASTERS_VIEW)
                  ? count("select count(*) from masters where status = 'PENDING_VERIFICATION'") : null,
            permissions.contains(AdminPermission.COMPLAINTS_VIEW)
                  ? count("select count(*) from complaints where status = 'OPEN'") : null);
   }

   private Long count(String sql) {
      return jdbc.queryForObject(sql, Long.class);
   }
}
