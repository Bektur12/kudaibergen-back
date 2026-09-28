package kg.kudaibergen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import kg.kudaibergen.media.MediaCleanupJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Профиль магазина: аватар, фото места (обложка, порядок, лимит), счётчики, отзывы с ответом, аватар пользователя. */
class ShopProfileIT extends AbstractIntegrationTest {

   @Autowired
   JdbcTemplate jdbc;

   @Test
   void аватарФотоМестаИОтзывы() throws Exception {
      String owner = accessToken("+996700800801", "SELLER");
      long container = jdbc.queryForObject("""
            select c.id from containers c join market_rows r on r.id = c.row_id
            where r.code = '18' and c.side = 'NORTH' and c.number = 12""", Long.class);
      long shopId = call(authed(jsonPost("/api/v1/shops", "{\"containerId\":" + container
            + ",\"name\":\"Фото Бокс\",\"brandIds\":[" + id("brands", "toyota") + "],\"categoryIds\":["
            + id("categories", "body") + "]}"), owner), 201).get("id").asLong();
      jdbc.update("update shops set status = 'ACTIVE', verified_at = now() where id = ?", shopId);

      long avatar = upload(owner, "AVATAR");
      JsonNode mine = call(authed(jsonPut("/api/v1/my/shop/avatar", "{\"mediaId\":" + avatar + "}"), owner), 200);
      assertThat(mine.get("avatarUrl").asText()).contains("-320.jpg");

      long first = upload(owner, "SHOP");
      long second = upload(owner, "SHOP");
      call(authed(jsonPost("/api/v1/my/shop/photos", "{\"mediaId\":" + first + "}"), owner), 200);
      JsonNode photos = call(authed(jsonPost("/api/v1/my/shop/photos", "{\"mediaId\":" + second + "}"), owner), 200);
      assertThat(photos.get(0).get("isCover").asBoolean()).isTrue();
      JsonNode cover = call(authed(post("/api/v1/my/shop/photos/" + second + "/cover"), owner), 200);
      assertThat(cover.get(0).get("id").asLong()).isEqualTo(second);

      // сотрудник не меняет фото места
      call(authed(jsonPost("/api/v1/my/shop/members", "{\"phone\":\"+996700800802\"}"), owner), 200);
      String staff = accessToken("+996700800802", "SELLER");
      call(authed(delete("/api/v1/my/shop/photos/" + first), staff), 403);

      JsonNode profile = call(get("/api/v1/shops/" + shopId), 200);
      assertThat(profile.get("avatarUrl").asText()).contains("-320.jpg");
      assertThat(profile.get("photos")).hasSize(2);
      assertThat(profile.get("counts").get("photos").asInt()).isEqualTo(2);
      assertThat(profile.get("counts").get("parts").asInt()).isZero();
      assertThat(call(get("/api/v1/shops/" + shopId + "/photos"), 200)).hasSize(2);

      // отзыв и один ответ владельца
      String buyer = accessToken("+996700800810", "BUYER");
      jdbc.update("""
            insert into reviews (shop_id, buyer_id, stars, tags)
            values (?, (select id from users where phone = '+996700800810'), 5, '{FAST_REPLY}')""", shopId);
      JsonNode reviews = call(get("/api/v1/shops/" + shopId + "/reviews"), 200).get("items");
      assertThat(reviews.get(0).get("tags").get(0).get("code").asText()).isEqualTo("FAST_REPLY");
      long reviewId = reviews.get(0).get("id").asLong();
      call(authed(jsonPost("/api/v1/my/shop/reviews/" + reviewId + "/reply", "{\"text\":\"Спасибо!\"}"), owner), 200);
      call(authed(jsonPost("/api/v1/my/shop/reviews/" + reviewId + "/reply", "{\"text\":\"Ещё раз\"}"), owner), 409);

      // аватар покупателя
      long me = upload(buyer, "AVATAR");
      JsonNode profileMe = call(authed(patch("/api/v1/me").contentType("application/json")
            .content("{\"avatarMediaId\":" + me + "}"), buyer), 200);
      assertThat(profileMe.get("avatarUrl").asText()).contains("-320.jpg");
      // чужое фото аватаром поставить нельзя
      call(authed(jsonPut("/api/v1/my/shop/avatar", "{\"mediaId\":" + me + "}"), owner), 400);
   }

   @Autowired
   MediaCleanupJob cleanup;

   @Test
   void неприкреплённыеФотоУдаляютсяЧерезСутки() throws Exception {
      String user = accessToken("+996700800820", "BUYER");
      long orphan = upload(user, "PART");
      long avatar = upload(user, "AVATAR");
      call(authed(patch("/api/v1/me").contentType("application/json")
            .content("{\"avatarMediaId\":" + avatar + "}"), user), 200);
      jdbc.update("update media set created_at = now() - interval '2 days' where id in (?, ?)", orphan, avatar);

      cleanup.cleanup();

      assertThat(jdbc.queryForObject("select count(*) from media where id = ?", Integer.class, orphan)).isZero();
      assertThat(jdbc.queryForObject("select count(*) from media where id = ?", Integer.class, avatar)).isEqualTo(1);
   }

   private long upload(String token, String purpose) throws Exception {
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", png);
      return call(authed(multipart("/api/v1/media/photos")
            .file(new MockMultipartFile("file", "p.png", "image/png", png.toByteArray()))
            .param("purpose", purpose), token), 201).get("id").asLong();
   }

   private long id(String table, String slug) {
      return jdbc.queryForObject("select id from " + table + " where slug = ?", Long.class, slug);
   }
}
