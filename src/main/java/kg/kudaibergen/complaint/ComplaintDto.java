package kg.kudaibergen.complaint;

import java.time.Instant;

public record ComplaintDto(Long id, Long authorId, ComplaintType type, Long targetId, String text,
                           ComplaintStatus status, String resolution, Instant createdAt) {

   public static ComplaintDto of(Complaint complaint) {
      return new ComplaintDto(complaint.getId(), complaint.getAuthorId(), complaint.getType(), complaint.getTargetId(),
            complaint.getText(), complaint.getStatus(), complaint.getResolution(), complaint.getCreatedAt());
   }
}
