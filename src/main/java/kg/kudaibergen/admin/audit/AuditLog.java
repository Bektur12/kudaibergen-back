package kg.kudaibergen.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Запись в журнал. Обычно через {@link Audited}; напрямую — для входа и смены пароля. */
@Component
public class AuditLog {

   static final int MAX_COMMENT = 1000;

   private final AdminAuditRepository entries;
   private final ObjectMapper objectMapper;

   public AuditLog(AdminAuditRepository entries, ObjectMapper objectMapper) {
      this.entries = entries;
      this.objectMapper = objectMapper;
   }

   public void record(@Nullable Long adminId, String action, String entityType, @Nullable Long entityId,
                      @Nullable Object before, @Nullable Object after, @Nullable String comment) {
      entries.save(new AdminAuditEntry(adminId, action, entityType, entityId, json(before), json(after),
            trim(comment), clientIp()));
   }

   private String json(Object value) {
      if (value == null) {
         return null;
      }
      try {
         return objectMapper.writeValueAsString(value);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException("Журнал: не удалось сохранить состояние объекта", e);
      }
   }

   private static String trim(String comment) {
      if (comment == null || comment.isBlank()) {
         return null;
      }
      String value = comment.strip();
      return value.length() <= MAX_COMMENT ? value : value.substring(0, MAX_COMMENT);
   }

   @Nullable
   static String clientIp() {
      if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
         HttpServletRequest request = attributes.getRequest();
         return request.getRemoteAddr();
      }
      return null;
   }
}
