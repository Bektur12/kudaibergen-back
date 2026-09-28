package kg.kudaibergen.chat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.chat.entity.Message;
import kg.kudaibergen.chat.entity.QuickReply;
import kg.kudaibergen.chat.realtime.CentrifugoTokens;
import kg.kudaibergen.chat.realtime.ChatChannels;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.notification.push.QuietHours;
import kg.kudaibergen.user.entity.Lang;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRulesTest {

   private static final String SECRET = "test-centrifugo-secret-32-bytes-minimum!!";
   private static final Instant NOW = Instant.parse("2026-09-29T06:00:00Z");

   @Test
   void каналыЧатаИЛичныйКанал() {
      assertThat(ChatChannels.chat(12L)).isEqualTo("chat:12");
      assertThat(ChatChannels.inbox(7L)).isEqualTo("inbox:7#7");
      assertThat(ChatChannels.chatIdOf("chat:12")).isEqualTo(12L);
      assertThat(ChatChannels.chatIdOf("inbox:7#7")).isNull();
      assertThat(ChatChannels.chatIdOf("chat:abc")).isNull();
   }

   @Test
   void токеныCentrifugoПодписаныОбщимКлючом() {
      CentrifugoTokens tokens = new CentrifugoTokens(properties(), Clock.fixed(Instant.now(), ZoneOffset.UTC));

      Claims connection = parse(tokens.connection(5L).token());
      assertThat(connection.getSubject()).isEqualTo("5");
      assertThat(connection.get("channel")).isNull();

      CentrifugoTokens.Token subscription = tokens.subscription(5L, "chat:12");
      Claims claims = parse(subscription.token());
      assertThat(claims.getSubject()).isEqualTo("5");
      assertThat(claims.get("channel", String.class)).isEqualTo("chat:12");
      assertThat(subscription.expiresInSeconds()).isEqualTo(3600);
   }

   @Test
   void сообщениеСдвигаетПоследнееИОтметкуСвоейСтороны() {
      Chat chat = new Chat(1L, 2L, 3L, NOW);
      Message fromShop = withId(Message.text(9L, ChatSide.SHOP, 20L, "Есть KYB", null, NOW), 10L);
      chat.messageAdded(fromShop);
      assertThat(chat.getLastMessageId()).isEqualTo(10L);
      assertThat(chat.getShopReadMessageId()).isEqualTo(10L);
      assertThat(chat.getBuyerReadMessageId()).isZero();
      assertThat(chat.getBuyerFirstMessageAt()).isNull();

      Instant later = NOW.plus(Duration.ofMinutes(1));
      chat.messageAdded(withId(Message.text(9L, ChatSide.BUYER, 1L, "Беру", null, later), 11L));
      assertThat(chat.getBuyerReadMessageId()).isEqualTo(11L);
      assertThat(chat.getBuyerFirstMessageAt()).isEqualTo(later);
   }

   @Test
   void прочтениеТолькоВперёд() {
      Chat chat = new Chat(1L, 2L, null, NOW);
      assertThat(chat.read(ChatSide.BUYER, 5)).isTrue();
      assertThat(chat.read(ChatSide.BUYER, 3)).isFalse();
      assertThat(chat.readMessageId(ChatSide.BUYER)).isEqualTo(5);
      assertThat(chat.readMessageId(ChatSide.SHOP)).isZero();
   }

   @Test
   void блокировкаОднойСтороныЗакрываетПереписку() {
      Chat chat = new Chat(1L, 2L, null, NOW);
      chat.setBlocked(ChatSide.SHOP, true);
      assertThat(chat.isBlocked()).isTrue();
      assertThat(chat.isBlockedBy(ChatSide.SHOP)).isTrue();
      assertThat(chat.isBlockedBy(ChatSide.BUYER)).isFalse();
      chat.setBlocked(ChatSide.SHOP, false);
      assertThat(chat.isBlocked()).isFalse();
   }

   @Test
   void быстрыеОтветыПоСторонамИЯзыкам() {
      assertThat(QuickReply.RESERVED.side()).isEqualTo(ChatSide.SHOP);
      assertThat(QuickReply.SOLD.kind()).isEqualTo(QuickReply.Kind.MESSAGE);
      assertThat(QuickReply.ARRIVED.side()).isEqualTo(ChatSide.BUYER);
      assertThat(QuickReply.CLOSE_REQUEST.kind()).isEqualTo(QuickReply.Kind.ACTION);
      assertThat(QuickReply.RESERVED.label(Lang.RU)).isEqualTo("Отложил для вас");
      assertThat(QuickReply.RESERVED.label(Lang.KG)).isEqualTo("Сиз үчүн калтырдым");
   }

   @Test
   void превьюПушаДляВложенийИПрихода() {
      Message photo = Message.media(1L, ChatSide.SHOP, 2L, kg.kudaibergen.chat.entity.MessageType.PHOTO, "вот",
            "k", "image/jpeg", null, null, null, NOW);
      assertThat(ChatTexts.preview(photo, Lang.RU)).isEqualTo("📷 Фото вот");
      Message arrived = Message.quick(1L, ChatSide.BUYER, 3L, QuickReply.ARRIVED, "Покупатель подошёл", null, null,
            NOW);
      assertThat(ChatTexts.preview(arrived, Lang.KG)).isEqualTo("Сатып алуучу келди");
   }

   @Test
   void тихиеЧасыС22До7() {
      assertThat(quietAt("2026-09-29T22:30:00+06:00")).isTrue();
      assertThat(quietAt("2026-09-29T06:59:00+06:00")).isTrue();
      assertThat(quietAt("2026-09-29T07:00:00+06:00")).isFalse();
      assertThat(quietAt("2026-09-29T21:59:00+06:00")).isFalse();
   }

   private static boolean quietAt(String time) {
      Instant instant = java.time.OffsetDateTime.parse(time).toInstant();
      return new QuietHours(Clock.fixed(instant, java.time.ZoneId.of("Asia/Bishkek"))).now();
   }

   private static Message withId(Message message, long id) {
      ReflectionTestUtils.setField(message, "id", id);
      return message;
   }

   private static Claims parse(String token) {
      return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).build()
            .parseSignedClaims(token).getPayload();
   }

   private static AppProperties properties() {
      return new AppProperties(null, null, null, null, null, null,
            new AppProperties.Centrifugo("http://localhost", "key", SECRET, Duration.ofHours(1)), null);
   }
}
