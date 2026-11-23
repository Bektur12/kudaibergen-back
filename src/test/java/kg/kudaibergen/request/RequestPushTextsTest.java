package kg.kudaibergen.request;

import kg.kudaibergen.user.entity.Lang;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestPushTextsTest {

   @Test
   void понятноЧтоЭтоЗаявкаПокупателя() {
      assertThat(RequestPushTexts.newRequestTitle("Camry 50 · 2012", Lang.RU)).isEqualTo("Новый запрос · Camry 50 · 2012");
      assertThat(RequestPushTexts.newRequestBody(" фара левая ", false, false, Lang.RU)).isEqualTo("Покупатель ищет: фара левая");
      assertThat(RequestPushTexts.newRequestBody(null, true, true, Lang.RU)).isEqualTo("Покупатель ищет запчасть — посмотрите фото");
      assertThat(RequestPushTexts.newRequestBody("", false, true, Lang.RU)).isEqualTo("Покупатель ищет запчасть — послушайте голосовое");
      assertThat(RequestPushTexts.newRequestTitle("Camry 50", Lang.KG)).startsWith("Жаңы суроо");
   }
}
