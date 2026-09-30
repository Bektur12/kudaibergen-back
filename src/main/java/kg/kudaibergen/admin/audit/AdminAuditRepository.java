package kg.kudaibergen.admin.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, Long> {
}
