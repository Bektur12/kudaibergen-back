package kg.kudaibergen.common.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

   public NotFoundException(String code, String message) {
      super(code, message, HttpStatus.NOT_FOUND, null);
   }
}
