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

   @Test
   void напоминанияРазныеБезЗанудства() {
      assertThat(RequestPushTexts.remindTitle("Camry 50", 1, Lang.RU)).isEqualTo("⏰ Покупатель ждёт ответа · Camry 50");
      assertThat(RequestPushTexts.remindTitle("Camry 50", 2, Lang.RU)).isEqualTo("🔔 Вы ещё не ответили · Camry 50");
      assertThat(RequestPushTexts.remindTitle("Camry 50", 3, Lang.RU)).isEqualTo("Успейте ответить первым · Camry 50");
      assertThat(RequestPushTexts.remindBody("фара", false, false, 1, Lang.RU)).isEqualTo("Покупатель ищет: фара. Есть у вас?");
      assertThat(RequestPushTexts.remindBody("фара", false, false, 3, Lang.RU))
            .isEqualTo("Всё ещё ищут: фара. Ответ займёт секунду");
   }
}
