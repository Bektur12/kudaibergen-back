package kg.kudaibergen.common.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

   public BadRequestException(String code, String message) {
      this(code, message, null);
   }

   public BadRequestException(String code, String message, String field) {
      super(code, message, HttpStatus.BAD_REQUEST, field);
   }
}
