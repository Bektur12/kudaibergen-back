package kg.kudaibergen.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Базовая ошибка домена: машинный код + HTTP-статус. Превращается в RFC 7807 problem+json,
 * где code и дополнительные свойства (attemptsLeft, retryAfter, …) идут полями верхнего уровня.
 */
public class ApiException extends RuntimeException {

   private final String code;
   private final HttpStatus status;
   private final Map<String, Object> properties = new LinkedHashMap<>();

   public ApiException(String code, String message, HttpStatus status) {
      super(message);
      this.code = code;
      this.status = status;
   }

   /** Дополнительное поле ответа об ошибке, например attemptsLeft для неверного кода. */
   public ApiException with(String name, Object value) {
      properties.put(name, value);
      return this;
   }

   public String getCode() {
      return code;
   }

   public HttpStatus getStatus() {
      return status;
   }

   public Map<String, Object> getProperties() {
      return properties;
   }
}
