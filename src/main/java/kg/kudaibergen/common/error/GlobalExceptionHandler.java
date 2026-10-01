package kg.kudaibergen.common.error;

import java.util.List;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Все ошибки — application/problem+json (RFC 7807) с полем code.
 * Стандартные исключения Spring MVC обрабатывает базовый класс, здесь только дописываем code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

   private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

   @ExceptionHandler(ApiException.class)
   public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
      ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatus());
      if (ex instanceof RateLimitException rateLimit) {
         response.header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimit.retryAfterSeconds()));
      }
      return response.body(Problems.of(ex));
   }

   @Override
   protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                 @NonNull HttpHeaders headers,
                                                                 @NonNull HttpStatusCode status,
                                                                 @NonNull WebRequest request) {
      List<Problems.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new Problems.FieldError(error.getField(), error.getDefaultMessage()))
            .toList();
      return ResponseEntity.badRequest().body(validation(errors));
   }

   @ExceptionHandler(ConstraintViolationException.class)
   public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex) {
      List<Problems.FieldError> errors = ex.getConstraintViolations().stream()
            .map(violation -> new Problems.FieldError(violation.getPropertyPath().toString(), violation.getMessage()))
            .toList();
      return ResponseEntity.badRequest().body(validation(errors));
   }

   @ExceptionHandler(AuthenticationException.class)
   public ResponseEntity<ProblemDetail> handleAuth(AuthenticationException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Problems.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется авторизация"));
   }

   @ExceptionHandler(AccessDeniedException.class)
   public ResponseEntity<ProblemDetail> handleDenied(AccessDeniedException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Problems.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "Нет доступа к ресурсу"));
   }

   /** Гонка на уникальных индексах (например, два магазина на один контейнер). */
   @ExceptionHandler(DataIntegrityViolationException.class)
   public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
      log.warn("Нарушение целостности данных: {}", ex.getMostSpecificCause().getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Problems.of(HttpStatus.CONFLICT, "CONFLICT", "Конфликт данных"));
   }

   @ExceptionHandler(Exception.class)
   public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
      log.error("Необработанная ошибка", ex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Внутренняя ошибка сервера"));
   }

   /** Ошибки самого Spring MVC (404 маршрута, 405, битый JSON, …): добавляем code по статусу. */
   @Override
   protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, @Nullable Object body,
                                                            @NonNull HttpHeaders headers,
                                                            @NonNull HttpStatusCode statusCode,
                                                            @NonNull WebRequest request) {
      ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
      if (response != null && response.getBody() instanceof ProblemDetail problem
            && problem.getProperties() == null) {
         String code = codeFor(statusCode);
         ProblemDetail withCode = Problems.of(statusCode, code, detailFor(statusCode));
         return ResponseEntity.status(statusCode).headers(response.getHeaders()).body(withCode);
      }
      return response;
   }

   private static ProblemDetail validation(List<Problems.FieldError> errors) {
      String detail = errors.isEmpty() ? "Некорректные данные" : errors.get(0).message();
      ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", detail);
      problem.setProperty(Problems.ERRORS, errors);
      return problem;
   }

   private static String codeFor(HttpStatusCode status) {
      return switch (status.value()) {
         case 404 -> "NOT_FOUND";
         case 405 -> "METHOD_NOT_ALLOWED";
         case 406, 415 -> "UNSUPPORTED_MEDIA_TYPE";
         case 413 -> "PAYLOAD_TOO_LARGE";
         default -> status.is4xxClientError() ? "BAD_REQUEST" : "INTERNAL_ERROR";
      };
   }

   private static String detailFor(HttpStatusCode status) {
      return switch (status.value()) {
         case 404 -> "Ресурс не найден";
         case 405 -> "Метод не поддерживается";
         case 406, 415 -> "Неподдерживаемый формат";
         case 413 -> "Слишком большой запрос";
         default -> status.is4xxClientError() ? "Некорректный запрос" : "Внутренняя ошибка сервера";
      };
   }
}
