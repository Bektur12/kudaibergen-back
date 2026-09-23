package kg.kudaibergen.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.security.CurrentUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Идемпотентность POST-ов одним местом, без копипасты в сервисах.
 * Ключ уникален в пределах пользователя: чужой ключ не может подменить ответ.
 */
@Aspect
@Component
public class IdempotencyAspect {

   public static final String HEADER = "Idempotency-Key";

   private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);
   private static final int MAX_CLIENT_KEY_LENGTH = 60;

   private final IdempotencyKeyRepository repository;
   private final ObjectMapper objectMapper;

   public IdempotencyAspect(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
      this.repository = repository;
      this.objectMapper = objectMapper;
   }

   @Around("@annotation(kg.kudaibergen.common.idempotency.Idempotent)")
   public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
      String clientKey = header();
      Long userId = CurrentUser.idOrNull();
      if (clientKey == null || clientKey.isBlank() || userId == null) {
         return joinPoint.proceed();
      }
      if (clientKey.length() > MAX_CLIENT_KEY_LENGTH) {
         throw new BadRequestException("IDEMPOTENCY_KEY_TOO_LONG",
               "Idempotency-Key длиннее " + MAX_CLIENT_KEY_LENGTH + " символов");
      }

      String storedKey = userId + ":" + clientKey;
      Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();

      var replay = repository.findById(storedKey);
      if (replay.isPresent()) {
         log.debug("Идемпотентный повтор по ключу {}", storedKey);
         return objectMapper.readValue(replay.get().getResponse(), returnType);
      }

      Object result = joinPoint.proceed();
      try {
         repository.saveAndFlush(new IdempotencyKey(storedKey, userId, objectMapper.writeValueAsString(result)));
      } catch (DataIntegrityViolationException parallelRequest) {
         log.debug("Ключ {} уже записан параллельным запросом", storedKey);
      }
      return result;
   }

   private String header() {
      if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
         return null;
      }
      HttpServletRequest request = attributes.getRequest();
      return request.getHeader(HEADER);
   }
}
