package kg.kudaibergen.admin.access;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/** Поле-телефон в DTO админки: без права PII_VIEW уходит маской (см. {@link Phones}). */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = MaskedPhone.Serializer.class)
public @interface MaskedPhone {

   class Serializer extends JsonSerializer<String> {

      @Override
      public void serialize(String phone, JsonGenerator generator, SerializerProvider provider) throws IOException {
         generator.writeString(Phones.forViewer(phone));
      }
   }
}
