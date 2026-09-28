package kg.kudaibergen.complaint;

import java.util.List;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.NotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplaintService {

   private final ComplaintRepository complaints;

   public ComplaintService(ComplaintRepository complaints) {
      this.complaints = complaints;
   }

   /** Повторная открытая жалоба того же автора на то же не плодит дублей. */
   @Transactional
   public ComplaintDto create(Long authorId, ComplaintType type, Long targetId, String text) {
      Complaint complaint = complaints.findFirstByAuthorIdAndTypeAndTargetIdAndStatus(authorId, type, targetId,
                  ComplaintStatus.OPEN)
            .orElseGet(() -> complaints.save(
                  new Complaint(authorId, type, targetId, text == null || text.isBlank() ? null : text.trim())));
      return ComplaintDto.of(complaint);
   }

   @Transactional(readOnly = true)
   public List<ComplaintDto> open(int limit) {
      return complaints.findByStatusOrderByCreatedAtAsc(ComplaintStatus.OPEN, PageRequest.of(0, limit)).stream()
            .map(ComplaintDto::of).toList();
   }

   @Transactional
   public ComplaintDto resolve(Long id, ComplaintStatus status, String resolution, Long adminId) {
      if (status == ComplaintStatus.OPEN) {
         throw new BadRequestException("BAD_STATUS", "Решение — RESOLVED или REJECTED");
      }
      Complaint complaint = complaints.findById(id)
            .orElseThrow(() -> new NotFoundException("COMPLAINT_NOT_FOUND", "Жалоба не найдена"));
      complaint.resolve(status, resolution, adminId);
      return ComplaintDto.of(complaint);
   }
}
