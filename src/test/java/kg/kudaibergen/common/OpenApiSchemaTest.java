package kg.kudaibergen.common;

import java.util.Map;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import kg.kudaibergen.common.config.OpenApiConfig;
import kg.kudaibergen.user.dto.MeResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Схемы для openapi-typescript: nullable по @Nullable, required у ответов, тела запросов не трогаем. */
class OpenApiSchemaTest {

   @Test
   @SuppressWarnings("rawtypes")
   void nullableПоАннотацииИСсылкаВAllOf() {
      Map<String, Schema> schemas = ModelConverters.getInstance().readAll(new AnnotatedType(MeResponse.class));
      OpenAPI openApi = new OpenAPI().components(new Components().schemas(schemas));
      OpenApiConfig.customise(openApi, OpenApiConfig.records("kg.kudaibergen"));
      Map<String, Schema> me = schemas.get("MeResponse").getProperties();

      assertThat(schemas.get("MeResponse").getRequired()).contains("id", "name", "shop", "hasShop");
      // сама модель ShopRef не становится nullable — только поле shop
      assertThat(schemas.get("ShopRef").getNullable()).isNotEqualTo(Boolean.TRUE);

      assertThat(me.get("name").getNullable()).isTrue();
      assertThat(me.get("avatarUrl").getNullable()).isTrue();
      assertThat(me.get("phone").getNullable()).isNotEqualTo(Boolean.TRUE);
      assertThat(me.get("id").getNullable()).isNotEqualTo(Boolean.TRUE);
      Schema shop = me.get("shop");
      assertThat(shop.getNullable()).isTrue();
      assertThat(((Schema<?>) shop.getAllOf().get(0)).get$ref()).endsWith("/ShopRef");
      Schema<?> status = (Schema<?>) schemas.get("ShopRef").getProperties().get("status");
      assertThat(status.getEnum()).map(String::valueOf).containsExactly("PENDING_VERIFICATION", "ACTIVE", "BLOCKED");
   }

   @Test
   void requiredТолькоУОтветов() {
      Schema<?> response = new ObjectSchema().addProperty("id", new StringSchema()).addProperty("name", new StringSchema());
      Schema<?> nested = new ObjectSchema().addProperty("year", new StringSchema());
      Schema<?> request = new ObjectSchema().addProperty("name", new StringSchema())
            .addProperty("car", new Schema<>().$ref("#/components/schemas/CarInput"));
      OpenAPI openApi = new OpenAPI()
            .components(new Components().addSchemas("Resp", response).addSchemas("Req", request)
                  .addSchemas("CarInput", nested))
            .paths(new Paths().addPathItem("/x", new PathItem().patch(new Operation().requestBody(new RequestBody()
                  .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/Req"))))))));

      OpenApiConfig.customise(openApi, Map.of());

      assertThat(response.getRequired()).containsExactly("id", "name");
      assertThat(request.getRequired()).isNull();
      assertThat(nested.getRequired()).isNull();
   }
}
