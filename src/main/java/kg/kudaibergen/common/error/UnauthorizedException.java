package kg.kudaibergen.common.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

   public UnauthorizedException(String code, String message) {
      super(code, message, HttpStatus.UNAUTHORIZED);
   }
}
