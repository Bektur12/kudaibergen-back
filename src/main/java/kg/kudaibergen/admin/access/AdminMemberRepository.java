package kg.kudaibergen.admin.access;

import kg.kudaibergen.common.security.AdminRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminMemberRepository extends JpaRepository<AdminMember, Long> {

   boolean existsByRoleAndActiveTrue(AdminRole role);
}
