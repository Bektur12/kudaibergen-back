package kg.kudaibergen.common.error;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Сборка RFC 7807. Клиент ветвится по полю code (UPPER_SNAKE), type — ссылка на описание
 * того же кода, detail — текст для показа пользователю как есть.
 */
public final class Problems {

   public static final String TYPE_BASE = "https://kudaibergen.kg/problems/";
   public static final String CODE = "code";
   public static final String ERRORS = "errors";

   private Problems() {
   }

   public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
      ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
      problem.setType(URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-')));
      problem.setProperty(CODE, code);
      return problem;
   }

   public static ProblemDetail of(ApiException ex) {
      ProblemDetail problem = of(ex.getStatus(), ex.getCode(), ex.getMessage());
      ex.getProperties().forEach(problem::setProperty);
      return problem;
   }

   /** Одна ошибка валидации поля для массива errors[]. */
   public record FieldError(String field, String message) {
   }
}
