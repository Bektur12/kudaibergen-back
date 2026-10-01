package kg.kudaibergen;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** Справочник марок (сид из brands.json) и гараж покупателя (экран 04). */
class GarageIT extends AbstractIntegrationTest {

   @Test
   void справочникОткрытГостюИСодержитСид() throws Exception {
      JsonNode brands = call(get("/api/v1/brands"), 200);
      assertThat(brands).hasSize(18);
      assertThat(brands.get(0).get("name").asText()).isEqualTo("Toyota");
      assertThat(brands.get(0).get("logoUrl").asText()).isEqualTo("/assets/brands/toyota.png");

      JsonNode popular = call(get("/api/v1/brands").param("popular", "true"), 200);
      assertThat(popular).hasSize(8);

      JsonNode mers = call(get("/api/v1/brands").param("q", "мерс"), 200);
      assertThat(mers.get(0).get("slug").asText()).isEqualTo("mercedes-benz");

      JsonNode camry = call(get("/api/v1/models").param("q", "камри 50"), 200);
      assertThat(camry).hasSize(1);
      assertThat(camry.get(0).get("model").get("label").asText()).isEqualTo("Camry 50");
      assertThat(camry.get(0).get("brand").get("name").asText()).isEqualTo("Toyota");

      long mercedesId = mers.get(0).get("id").asLong();
      JsonNode models = call(get("/api/v1/brands/" + mercedesId + "/models"), 200);
      assertThat(models.findValuesAsText("label")).contains("E-класс W211", "E-класс W212", "Sprinter");

      mvcLogoAvailable();
   }

   @Test
   void гаражОсновнаяМашинаЛимитИПроверкаГода() throws Exception {
      String access = accessToken("+996700200201", "BUYER");
      long camry50 = modelId("камри 50");
      long fitGd = modelId("фит gd");

      JsonNode first = call(authed(jsonPost("/api/v1/me/cars",
            "{\"modelId\":" + camry50 + ",\"year\":2012,\"engine\":\"2.5 бензин\"}"), access), 201);
      assertThat(first.get("isPrimary").asBoolean()).isTrue();
      assertThat(first.get("title").asText()).isEqualTo("Toyota Camry 50");
      assertThat(first.get("subtitle").asText()).isEqualTo("2012 · 2.5 бензин");
      assertThat(first.get("label").asText()).isEqualTo("Camry 50 · 2012");

      JsonNode wrongYear = call(authed(jsonPost("/api/v1/me/cars",
            "{\"modelId\":" + fitGd + ",\"year\":2015}"), access), 400);
      assertThat(wrongYear.get("code").asText()).isEqualTo("YEAR_OUT_OF_RANGE");
      assertThat(wrongYear.get("yearFrom").asInt()).isEqualTo(2001);

      JsonNode fit = call(authed(jsonPost("/api/v1/me/cars",
            "{\"modelId\":" + fitGd + ",\"year\":2009,\"engine\":\"1.3\",\"vin\":\"gd1-1234567\"}"), access), 201);
      assertThat(fit.get("isPrimary").asBoolean()).isFalse();
      assertThat(fit.get("vin").asText()).isEqualTo("GD1-1234567");

      // сделать основной и переименовать
      call(authed(jsonPost("/api/v1/me/cars/" + fit.get("id").asLong() + "/primary", "{}"), access), 200);
      JsonNode renamed = call(authed(patch("/api/v1/me/cars/" + fit.get("id").asLong())
            .contentType(MediaType.APPLICATION_JSON).characterEncoding("UTF-8")
            .content("{\"name\":\"Фитик жены\"}"), access), 200);
      assertThat(renamed.get("title").asText()).isEqualTo("Фитик жены");

      JsonNode garage = call(authed(get("/api/v1/me/cars"), access), 200);
      assertThat(garage.get(0).get("id").asLong()).isEqualTo(fit.get("id").asLong());
      assertThat(garage.get(1).get("isPrimary").asBoolean()).isFalse();

      // удалили основную — основной стала оставшаяся
      call(authed(delete("/api/v1/me/cars/" + fit.get("id").asLong()), access), 204);
      JsonNode afterDelete = call(authed(get("/api/v1/me/cars"), access), 200);
      assertThat(afterDelete).hasSize(1);
      assertThat(afterDelete.get(0).get("isPrimary").asBoolean()).isTrue();

      // лимит 10 машин
      for (int i = 0; i < 9; i++) {
         call(authed(jsonPost("/api/v1/me/cars", "{\"modelId\":" + camry50 + ",\"year\":2014}"), access), 201);
      }
      JsonNode limit = call(authed(jsonPost("/api/v1/me/cars",
            "{\"modelId\":" + camry50 + ",\"year\":2014}"), access), 409);
      assertThat(limit.get("code").asText()).isEqualTo("GARAGE_LIMIT");
   }

   @Test
   void чужуюМашинуНельзяНиВидетьНиМенять() throws Exception {
      String owner = accessToken("+996700200202", "BUYER");
      String stranger = accessToken("+996700200203", "BUYER");
      JsonNode car = call(authed(jsonPost("/api/v1/me/cars",
            "{\"modelId\":" + modelId("камри 50") + ",\"year\":2013}"), owner), 201);

      call(authed(delete("/api/v1/me/cars/" + car.get("id").asLong()), stranger), 404);
      call(authed(jsonPost("/api/v1/me/cars/" + car.get("id").asLong() + "/primary", "{}"), stranger), 404);
      assertThat(call(authed(get("/api/v1/me/cars"), stranger), 200)).isEmpty();
      // гость в гараж не попадает
      call(get("/api/v1/me/cars"), 401);
   }

   private long modelId(String query) throws Exception {
      return call(get("/api/v1/models").param("q", query), 200).get(0).get("model").get("id").asLong();
   }

   private void mvcLogoAvailable() throws Exception {
      int status = mvc.perform(get("/assets/brands/toyota.png")).andReturn().getResponse().getStatus();
      assertThat(status).isEqualTo(200);
   }
}
