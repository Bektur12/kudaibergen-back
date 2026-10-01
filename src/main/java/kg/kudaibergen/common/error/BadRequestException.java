package kg.kudaibergen.common.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

   public BadRequestException(String code, String message) {
      super(code, message, HttpStatus.BAD_REQUEST);
   }
}
