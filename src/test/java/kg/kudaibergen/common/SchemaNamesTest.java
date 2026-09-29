package kg.kudaibergen.common;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * springdoc называет схему OpenAPI коротким именем класса: два вложенных record «Counts» в разных DTO
 * дают одну схему, и типы фронта (openapi-typescript) для одного из экранов молча становятся неверными.
 * Поэтому короткие имена классов в коде уникальны; исключения — типы, которые наружу не отдаются.
 */
class SchemaNamesTest {

   /** Не попадают в API: настройки, внутренние события и промежуточные результаты. */
   private static final Set<String> INTERNAL = Set.of(
         "kg.kudaibergen.common.config.AppProperties$Media",
         "kg.kudaibergen.chat.ChatEvents$Read",
         "kg.kudaibergen.catalog.importing.PartImportMapper$Result",
         "kg.kudaibergen.media.ImageProcessor$Result");

   @Test
   void короткиеИменаКлассовУникальны() {
      ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
         @Override
         protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
            return true;
         }
      };
      scanner.addIncludeFilter((TypeFilter) (reader, factory) -> true);

      Map<String, Set<String>> byName = new TreeMap<>();
      for (BeanDefinition definition : scanner.findCandidateComponents("kg.kudaibergen")) {
         String className = definition.getBeanClassName();
         if (className == null || INTERNAL.contains(className) || className.matches(".*\\$\\d+.*")) {
            continue;
         }
         String simple = className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1);
         byName.computeIfAbsent(simple, name -> new TreeSet<>()).add(className);
      }
      byName.values().removeIf(classes -> classes.size() < 2);
      assertThat(byName).as("одинаковые короткие имена — переименуйте вложенный тип").isEmpty();
   }
}
