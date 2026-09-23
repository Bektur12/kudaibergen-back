package kg.kudaibergen.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

   public ConflictException(String code, String message) {
      super(code, message, HttpStatus.CONFLICT, null);
   }
}
