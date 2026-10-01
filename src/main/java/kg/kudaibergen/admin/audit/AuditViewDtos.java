package kg.kudaibergen.admin.audit;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.lang.Nullable;

/** Журнал действий админки. */
public final class AuditViewDtos {

   private AuditViewDtos() {
   }

   public record AuditRow(Long id, @Nullable Long adminId, @Nullable String adminName, String action,
                          String entityType, @Nullable Long entityId, @Nullable String comment, @Nullable String ip,
                          Instant createdAt) {
   }

   /** Счётчики: today — сегодня (Бишкек), week — за 7 дней (с учётом фильтров). */
   public record AuditCounts(long today, long week) {
   }

   /** Запись целиком: состояние объекта до и после (JSON как его видел сотрудник). */
   public record AuditEntryDto(Long id, @Nullable Long adminId, @Nullable String adminName, String action,
                               String entityType, @Nullable Long entityId, @Nullable JsonNode before,
                               @Nullable JsonNode after, @Nullable String comment, @Nullable String ip,
                               Instant createdAt) {
   }
}
