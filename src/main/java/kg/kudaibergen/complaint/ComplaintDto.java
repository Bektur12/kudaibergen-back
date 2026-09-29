package kg.kudaibergen.complaint;

import java.time.Instant;
import org.springframework.lang.Nullable;

public record ComplaintDto(Long id, @Nullable Long authorId, ComplaintType type, Long targetId, @Nullable String text,
                           ComplaintStatus status, @Nullable String resolution, Instant createdAt) {

   public static ComplaintDto of(Complaint complaint) {
      return new ComplaintDto(complaint.getId(), complaint.getAuthorId(), complaint.getType(), complaint.getTargetId(),
            complaint.getText(), complaint.getStatus(), complaint.getResolution(), complaint.getCreatedAt());
   }
}
