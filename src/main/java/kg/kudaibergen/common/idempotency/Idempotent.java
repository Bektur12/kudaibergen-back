package kg.kudaibergen.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Метод контроллера защищён от дублей: при повторном вызове с тем же
 * заголовком Idempotency-Key возвращается сохранённый ответ, ничего не создаётся.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
