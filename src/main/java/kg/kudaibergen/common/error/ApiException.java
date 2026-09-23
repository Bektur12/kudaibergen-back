package kg.kudaibergen.common.error;

import org.springframework.http.HttpStatus;

/** Базовая ошибка домена: код + HTTP-статус + (опционально) поле. */
public class ApiException extends RuntimeException {

   private final String code;
   private final HttpStatus status;
   private final String field;

   public ApiException(String code, String message, HttpStatus status, String field) {
      super(message);
      this.code = code;
      this.status = status;
      this.field = field;
   }

   public String getCode() {
      return code;
   }

   public HttpStatus getStatus() {
      return status;
   }

   public String getField() {
      return field;
   }
}
