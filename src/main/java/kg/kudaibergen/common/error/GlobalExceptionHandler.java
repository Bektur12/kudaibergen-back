package kg.kudaibergen.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

   private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

   @ExceptionHandler(ApiException.class)
   public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex) {
      return ResponseEntity.status(ex.getStatus())
            .body(new ApiErrorResponse(ex.getCode(), ex.getMessage(), ex.getField()));
   }

   @ExceptionHandler(MethodArgumentNotValidException.class)
   public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
      var error = ex.getBindingResult().getFieldErrors().stream().findFirst();
      String field = error.map(f -> f.getField()).orElse(null);
      String message = error.map(f -> f.getDefaultMessage()).orElse("Некорректные данные");
      return ResponseEntity.badRequest().body(new ApiErrorResponse("VALIDATION_ERROR", message, field));
   }

   @ExceptionHandler(ConstraintViolationException.class)
   public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex) {
      var violation = ex.getConstraintViolations().stream().findFirst();
      String field = violation.map(v -> v.getPropertyPath().toString()).orElse(null);
      String message = violation.map(ConstraintViolation::getMessage).orElse("Некорректные данные");
      return ResponseEntity.badRequest().body(new ApiErrorResponse("VALIDATION_ERROR", message, field));
   }

   @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
         MissingRequestHeaderException.class })
   public ResponseEntity<ApiErrorResponse> handleBadInput(Exception ex) {
      return ResponseEntity.badRequest()
            .body(new ApiErrorResponse("BAD_REQUEST", "Некорректный запрос", null));
   }

   @ExceptionHandler(MaxUploadSizeExceededException.class)
   public ResponseEntity<ApiErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
      return ResponseEntity.badRequest()
            .body(new ApiErrorResponse("FILE_TOO_LARGE", "Файл больше допустимого размера", "file"));
   }

   @ExceptionHandler(MultipartException.class)
   public ResponseEntity<ApiErrorResponse> handleMultipart(MultipartException ex) {
      return ResponseEntity.badRequest()
            .body(new ApiErrorResponse("BAD_REQUEST", "Некорректная загрузка файла", null));
   }

   /** Клиент не приложил обязательную часть multipart-запроса (например, сам файл). */
   @ExceptionHandler(MissingServletRequestPartException.class)
   public ResponseEntity<ApiErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
      return ResponseEntity.badRequest()
            .body(new ApiErrorResponse("FILE_REQUIRED", "Файл не передан", ex.getRequestPartName()));
   }

   @ExceptionHandler(AuthenticationException.class)
   public ResponseEntity<ApiErrorResponse> handleAuth(AuthenticationException ex) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ApiErrorResponse("UNAUTHORIZED", "Требуется авторизация", null));
   }

   @ExceptionHandler(AccessDeniedException.class)
   public ResponseEntity<ApiErrorResponse> handleDenied(AccessDeniedException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiErrorResponse("FORBIDDEN", "Нет доступа к ресурсу", null));
   }

   @ExceptionHandler(NoResourceFoundException.class)
   public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ApiErrorResponse("NOT_FOUND", "Ресурс не найден", null));
   }

   /** Гонка на уникальных индексах (например, два оффера одного магазина на один запрос). */
   @ExceptionHandler(DataIntegrityViolationException.class)
   public ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException ex) {
      log.warn("Нарушение целостности данных: {}", ex.getMostSpecificCause().getMessage());
      return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ApiErrorResponse("CONFLICT", "Конфликт данных", null));
   }

   @ExceptionHandler(Exception.class)
   public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
      log.error("Необработанная ошибка", ex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiErrorResponse("INTERNAL_ERROR", "Внутренняя ошибка сервера", null));
   }
}
