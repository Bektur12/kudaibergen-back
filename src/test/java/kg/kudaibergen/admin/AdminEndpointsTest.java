package kg.kudaibergen.admin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.common.security.AdminPermission;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Каждый эндпоинт /api/v1/admin/** (кроме входа) закрыт @PreAuthorize по праву, а каждое изменяющее
 * действие пишется в журнал (@Audited). Новый эндпоинт без них роняет этот тест.
 */
class AdminEndpointsTest {

   private static final String ADMIN = "/api/v1/admin";
   private static final String ADMIN_AUTH = "/api/v1/admin/auth";
   private static final Set<RequestMethod> MUTATING =
         EnumSet.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);
   private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

   @Test
   void уКаждогоЭндпоинтаАдминкиЕстьПравоАУИзменяющихЕщёИЖурнал() throws Exception {
      List<Endpoint> endpoints = adminEndpoints();
      assertThat(endpoints).hasSizeGreaterThan(10);

      List<String> withoutPermission = new ArrayList<>();
      List<String> withoutAudit = new ArrayList<>();
      List<String> badExpressions = new ArrayList<>();
      for (Endpoint endpoint : endpoints) {
         PreAuthorize rule = AnnotatedElementUtils.findMergedAnnotation(endpoint.method(), PreAuthorize.class);
         if (rule == null) {
            rule = AnnotatedElementUtils.findMergedAnnotation(endpoint.method().getDeclaringClass(), PreAuthorize.class);
         }
         if (rule == null) {
            withoutPermission.add(endpoint.name());
         } else if (!validExpression(rule.value())) {
            badExpressions.add(endpoint.name() + ": " + rule.value());
         }
         boolean mutating = endpoint.verbs().stream().anyMatch(MUTATING::contains);
         if (mutating && !endpoint.method().isAnnotationPresent(Audited.class)) {
            withoutAudit.add(endpoint.name());
         }
      }
      assertThat(withoutPermission).as("эндпоинты админки без @PreAuthorize").isEmpty();
      assertThat(badExpressions).as("проверки не по праву AdminPermission").isEmpty();
      assertThat(withoutAudit).as("изменяющие эндпоинты админки без @Audited").isEmpty();
   }

   /** Только hasAuthority / hasAnyAuthority с существующими правами или hasRole('ADMIN') — без проверок по роли. */
   private static boolean validExpression(String expression) {
      if (expression.equals("hasRole('ADMIN')")) {
         return true;
      }
      if (!expression.startsWith("hasAuthority(") && !expression.startsWith("hasAnyAuthority(")) {
         return false;
      }
      Set<String> known = Arrays.stream(AdminPermission.values()).map(Enum::name).collect(Collectors.toSet());
      Matcher matcher = QUOTED.matcher(expression);
      boolean any = false;
      while (matcher.find()) {
         any = true;
         if (!known.contains(matcher.group(1))) {
            return false;
         }
      }
      return any;
   }

   private static List<Endpoint> adminEndpoints() throws ClassNotFoundException {
      var scanner = new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
      List<Endpoint> result = new ArrayList<>();
      for (var candidate : scanner.findCandidateComponents("kg.kudaibergen")) {
         Class<?> type = ClassUtils.forName(candidate.getBeanClassName(), AdminEndpointsTest.class.getClassLoader());
         RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class);
         String[] prefixes = base == null || base.path().length == 0 ? new String[]{""} : base.path();
         for (Method method : type.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
               continue;
            }
            String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
            for (String prefix : prefixes) {
               for (String path : paths) {
                  String full = prefix + path;
                  if (full.startsWith(ADMIN) && !full.startsWith(ADMIN_AUTH)) {
                     result.add(new Endpoint(type.getSimpleName() + "." + method.getName() + " " + full, method,
                           Set.of(mapping.method())));
                  }
               }
            }
         }
      }
      return result;
   }

   private record Endpoint(String name, Method method, Set<RequestMethod> verbs) {
   }
}
