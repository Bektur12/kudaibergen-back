package kg.kudaibergen.common.error;

import org.springframework.http.HttpStatus;

public class RateLimitException extends ApiException {

   public RateLimitException(String code, String message) {
      super(code, message, HttpStatus.TOO_MANY_REQUESTS, null);
   }
}
