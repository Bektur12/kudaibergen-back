package kg.kudaibergen.common.config;

import java.lang.reflect.RecordComponent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * OpenAPI для генерации типов фронта (openapi-typescript). Ответы отдают все поля всегда
 * ({@code default-property-inclusion: always}), поэтому в схемах ответов все поля — required;
 * поле, которое бывает null, помечено {@link Nullable} и получает {@code nullable: true}.
 * Схемы тел запросов не трогаем: обязательность там задают @NotNull / @NotBlank, остальное — необязательно.
 */
@Configuration
public class OpenApiConfig {

   private static final String BEARER = "bearerAuth";
   private static final String REF_PREFIX = "#/components/schemas/";
   private static final String BASE_PACKAGE = "kg.kudaibergen";

   @Bean
   public OpenAPI kudaibergenOpenApi() {
      return new OpenAPI()
            .info(new Info()
                  .title("Kudaibergen API")
                  .version("v2")
                  .description("Рынок автозапчастей «Кудайберген»: запросы продавцам, каталог, чаты, карта рынка"))
            .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                  .type(SecurityScheme.Type.HTTP)
                  .scheme("bearer")
                  .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER));
   }

   /**
    * Постобработка схем. Nullable — по {@link Nullable} на компоненте record: схема находится по короткому
    * имени класса (они уникальны — SchemaNamesTest). Ссылку на другую схему оборачиваем в allOf, иначе флаг
    * рядом с $ref теряется. Required — все поля схем ответов.
    */
   @Bean
   public OpenApiCustomizer nullableAndRequired() {
      Map<String, Class<?>> records = records(BASE_PACKAGE);
      return openApi -> customise(openApi, records);
   }

   public static void customise(OpenAPI openApi, Map<String, Class<?>> records) {
      Map<String, Schema> schemas = openApi.getComponents() == null ? null : openApi.getComponents().getSchemas();
      if (schemas == null) {
         return;
      }
      Set<String> requestSchemas = requestSchemas(openApi, schemas);
      schemas.forEach((name, schema) -> {
         Map<String, Schema> properties = schema.getProperties();
         if (properties == null || properties.isEmpty()) {
            return;
         }
         Class<?> type = recordOf(name, records);
         if (type != null) {
            for (RecordComponent component : type.getRecordComponents()) {
               Schema<?> property = properties.get(component.getName());
               if (property != null && component.getAccessor().isAnnotationPresent(Nullable.class)) {
                  properties.put(component.getName(), nullable(property));
               }
            }
         }
         if (!requestSchemas.contains(name)) {
            schema.setRequired(new ArrayList<>(properties.keySet()));
         }
      });
   }

   /**
    * record для схемы: по точному имени, а для generic-record — по префиксу: springdoc называет
    * CursorPage&lt;RequestSummaryDto&gt; схемой «CursorPageRequestSummaryDto».
    */
   private static Class<?> recordOf(String schemaName, Map<String, Class<?>> records) {
      Class<?> exact = records.get(schemaName);
      if (exact != null) {
         return exact;
      }
      return records.values().stream()
            .filter(type -> type.getTypeParameters().length > 0 && schemaName.startsWith(type.getSimpleName()))
            .max(Comparator.comparingInt(type -> type.getSimpleName().length()))
            .orElse(null);
   }

   private static Schema<?> nullable(Schema<?> property) {
      if (property.get$ref() == null) {
         property.setNullable(true);
         return property;
      }
      ComposedSchema wrapped = new ComposedSchema();
      wrapped.addAllOfItem(new Schema<>().$ref(property.get$ref()));
      wrapped.setNullable(true);
      return wrapped;
   }

   /** record-классы проекта по короткому имени — имя схемы в OpenAPI. */
   public static Map<String, Class<?>> records(String basePackage) {
      ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
         @Override
         protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
            return definition.getMetadata().getSuperClassName() != null
                  && definition.getMetadata().getSuperClassName().equals(Record.class.getName());
         }
      };
      scanner.addIncludeFilter((reader, factory) -> true);
      Map<String, Class<?>> result = new HashMap<>();
      for (BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
         Class<?> type = ClassUtils.resolveClassName(Objects.requireNonNull(definition.getBeanClassName()),
               OpenApiConfig.class.getClassLoader());
         result.put(type.getSimpleName(), type);
      }
      return result;
   }

   /** Схемы, до которых можно дойти из тел запросов (вместе с вложенными). */
   private static Set<String> requestSchemas(OpenAPI openApi, Map<String, Schema> schemas) {
      Deque<String> queue = new ArrayDeque<>();
      if (openApi.getPaths() != null) {
         for (PathItem path : openApi.getPaths().values()) {
            for (Operation operation : path.readOperations()) {
               if (operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) {
                  continue;
               }
               for (MediaType media : operation.getRequestBody().getContent().values()) {
                  collectRefs(media.getSchema(), queue);
               }
            }
         }
      }
      Set<String> seen = new HashSet<>();
      while (!queue.isEmpty()) {
         String name = queue.poll();
         if (seen.add(name)) {
            collectRefs(schemas.get(name), queue);
         }
      }
      return seen;
   }

   private static void collectRefs(Schema<?> schema, Deque<String> into) {
      if (schema == null) {
         return;
      }
      if (schema.get$ref() != null && schema.get$ref().startsWith(REF_PREFIX)) {
         into.add(schema.get$ref().substring(REF_PREFIX.length()));
      }
      if (schema.getProperties() != null) {
         schema.getProperties().values().forEach(property -> collectRefs(property, into));
      }
      collectRefs(schema.getItems(), into);
      if (schema.getAdditionalProperties() instanceof Schema<?> additional) {
         collectRefs(additional, into);
      }
      for (List<Schema> parts : Arrays.asList(schema.getAllOf(), schema.getAnyOf(), schema.getOneOf())) {
         if (parts != null) {
            parts.stream().filter(Objects::nonNull).forEach(part -> collectRefs(part, into));
         }
      }
   }
}
