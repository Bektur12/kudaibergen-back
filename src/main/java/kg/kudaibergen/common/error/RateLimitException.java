package kg.kudaibergen.common.error;

import org.springframework.http.HttpStatus;

/** 429. retryAfter (секунды) уходит и полем ответа, и заголовком Retry-After. */
public class RateLimitException extends ApiException {

   public RateLimitException(String code, String message, long retryAfterSeconds) {
      super(code, message, HttpStatus.TOO_MANY_REQUESTS);
      with("retryAfter", retryAfterSeconds);
   }

   public long retryAfterSeconds() {
      return (Long) getProperties().get("retryAfter");
   }
}
