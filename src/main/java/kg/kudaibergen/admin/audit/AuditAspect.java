package kg.kudaibergen.admin.audit;

import java.lang.reflect.Method;

import kg.kudaibergen.common.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Оборачивает действие {@link Audited} в транзакцию, в которую входят и сервисы (@Transactional REQUIRED),
 * и запись журнала: действие и строка журнала сохраняются вместе или не сохраняются вовсе.
 * Упавшее действие ничего не изменило — в журнал не пишется.
 */
@Aspect
@Component
public class AuditAspect {

   private static final ExpressionParser PARSER = new SpelExpressionParser();
   private static final ParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();

   private final AuditLog log;
   private final TransactionTemplate transaction;

   public AuditAspect(AuditLog log, PlatformTransactionManager transactionManager) {
      this.log = log;
      this.transaction = new TransactionTemplate(transactionManager);
   }

   @Around("@annotation(audited)")
   public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
      AuditTrail.Entry entry = AuditTrail.open();
      try {
         return transaction.execute(status -> {
            Object result = proceed(joinPoint);
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            Long entityId = entry.entityId != null ? entry.entityId
                  : evaluate(audited.id(), method, joinPoint.getArgs(), Long.class);
            String comment = entry.comment != null ? entry.comment
                  : evaluate(audited.comment(), method, joinPoint.getArgs(), String.class);
            Object after = entry.afterSet ? entry.after : snapshot(result);
            log.record(CurrentUser.idOrNull(), audited.action(), audited.entity(), entityId, entry.before, after,
                  comment);
            return result;
         });
      } catch (Failure failure) {
         throw failure.getCause();
      } finally {
         AuditTrail.close();
      }
   }

   private static Object proceed(ProceedingJoinPoint joinPoint) {
      try {
         return joinPoint.proceed();
      } catch (RuntimeException | Error e) {
         throw e;
      } catch (Throwable checked) {
         throw new Failure(checked);
      }
   }

   /** Тело ответа как «после»; бинарные ответы (PNG, xlsx) в журнал не кладём. */
   private static Object snapshot(Object result) {
      Object body = result instanceof ResponseEntity<?> response ? response.getBody() : result;
      return body instanceof byte[] ? null : body;
   }

   private static <T> T evaluate(String expression, Method method, Object[] args, Class<T> type) {
      if (expression.isEmpty()) {
         return null;
      }
      var context = new MethodBasedEvaluationContext(null, method, args, NAMES);
      return PARSER.parseExpression(expression).getValue(context, type);
   }

   /** Проверяемое исключение изнутри TransactionTemplate — пробрасываем наружу как было. */
   private static final class Failure extends RuntimeException {
      Failure(Throwable cause) {
         super(cause);
      }
   }
}
