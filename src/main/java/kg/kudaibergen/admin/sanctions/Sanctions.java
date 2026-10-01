package kg.kudaibergen.admin.sanctions;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** История санкций. Предупреждение действует {@link #WARNING_TTL} — пока оно есть, статус «Предупреждён». */
@Service
public class Sanctions {

   public static final Duration WARNING_TTL = Duration.ofDays(90);
   static final int HISTORY = 20;

   private final SanctionRepository sanctions;

   public Sanctions(SanctionRepository sanctions) {
      this.sanctions = sanctions;
   }

   @Transactional
   public Sanction record(SanctionTarget target, Long targetId, SanctionType type, String reason, Long adminId) {
      Instant activeUntil = type == SanctionType.WARNING ? Instant.now().plus(WARNING_TTL) : null;
      return sanctions.save(new Sanction(target, targetId, type, reason, adminId, activeUntil));
   }

   @Transactional(readOnly = true)
   public Set<Long> warned(SanctionTarget target, Collection<Long> ids) {
      if (ids.isEmpty()) {
         return Set.of();
      }
      return Set.copyOf(sanctions.findWarned(target, ids, Instant.now()));
   }

   @Transactional(readOnly = true)
   public long warningsSince(SanctionTarget target, Long id, Instant since) {
      return sanctions.countWarningsSince(target, id, since);
   }

   @Transactional(readOnly = true)
   public List<SanctionDto> history(SanctionTarget target, Long id) {
      return sanctions.findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(target, id, PageRequest.of(0, HISTORY))
            .stream().map(SanctionDto::of).toList();
   }
}
