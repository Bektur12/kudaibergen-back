package kg.kudaibergen.complaint;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

   List<Complaint> findByStatusOrderByCreatedAtAsc(ComplaintStatus status, Pageable page);

   java.util.Optional<Complaint> findFirstByAuthorIdAndTypeAndTargetIdAndStatus(Long authorId, ComplaintType type,
                                                                              Long targetId, ComplaintStatus status);
}
