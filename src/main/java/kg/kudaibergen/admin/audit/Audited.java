package kg.kudaibergen.admin.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Изменяющее действие админки — пишется в журнал (admin_audit_log) в одной транзакции с самим действием:
 * не записался журнал — откатилось и действие. Ставится на методы контроллеров /api/v1/admin/**;
 * тест AdminEndpointsTest падает, если у POST/PUT/PATCH/DELETE его нет.
 *
 * <p>id и comment — SpEL над аргументами метода ({@code "#id"}, {@code "#request.comment"}).
 * Состояние «до» метод передаёт через {@link AuditTrail#before}; «после» — возвращённый объект,
 * если метод не задал его сам через {@link AuditTrail#after}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

   /** Код действия, UPPER_SNAKE: SHOP_APPROVE, CONTAINER_UPDATE… */
   String action();

   /** Тип объекта: SHOP, MASTER, CONTAINER, COMPLAINT… */
   String entity();

   /** SpEL — id объекта. Пусто — метод задаёт его через AuditTrail.entityId или объекта нет. */
   String id() default "";

   /** SpEL — комментарий для журнала (поле «Комментарий для журнала…» и причины). */
   String comment() default "";
}
