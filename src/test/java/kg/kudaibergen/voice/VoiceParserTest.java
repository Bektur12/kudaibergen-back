package kg.kudaibergen.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import kg.kudaibergen.master.entity.ServiceWhen;
import kg.kudaibergen.request.entity.PartCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VoiceParserTest {

   private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
   /** Среда, 7 октября, 10:24 — как на пуше 12. */
   private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 10, 7, 10, 24, 0, 0, BISHKEK);

   @Test
   void replyFromDesign() {
      VoiceParser.ReplyDraft draft = VoiceParser.reply("Есть, оригинал KYB, новые, пара — три двести");
      assertThat(draft.price()).isEqualTo(3200);
      assertThat(draft.condition()).isEqualTo(PartCondition.NEW);
      assertThat(draft.original()).isTrue();
      assertThat(draft.manufacturer()).isEqualTo("KYB");
      assertThat(draft.tags()).containsExactly("Пара");
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "Есть, три тысячи двести | 3200",
         "Есть, 3200 сом | 3200",
         "Есть, 3 200 | 3200",
         "есть 3-200 | 3200",
         "есть, за четыре пятьсот | 4500",
         "есть полторы тысячи | 1500",
         "есть полторы | 1500",
         "две с половиной тысячи | 2500",
         "есть, тысяча восемьсот, б/у | 1800",
         "есть две штуки по тысяче восемьсот | 1800",
         "есть, пятьсот сом | 500",
         "сто двадцать тысяч | 120000",
         "бар, үч миң эки жүз сом | 3200",
         "бар, бир жарым миң | 1500",
         "есть 3к | 3000",
         "есть, 2012 года, за 7000 | 7000",
   })
   void price(String transcript, int price) {
      assertThat(VoiceParser.reply(transcript).price()).isEqualTo(price);
   }

   @Test
   void noPriceWhenNotSaid() {
      assertThat(VoiceParser.reply("есть, новые, оригинал").price()).isNull();
      assertThat(VoiceParser.reply("есть две штуки").price()).isNull();
      assertThat(VoiceParser.reply(null).price()).isNull();
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "Б/у, контрактные | USED",
         "бу японские | USED",
         "с разбора | USED",
         "новые | NEW",
         "под заказ, через два дня | ON_ORDER",
         "жаңы | NEW",
   })
   void condition(String transcript, PartCondition condition) {
      assertThat(VoiceParser.reply(transcript).condition()).isEqualTo(condition);
   }

   @Test
   void originalOrCopy() {
      assertThat(VoiceParser.reply("не оригинал, но хорошие").original()).isFalse();
      assertThat(VoiceParser.reply("копия").original()).isFalse();
      assertThat(VoiceParser.reply("родные").original()).isTrue();
      assertThat(VoiceParser.reply("есть").original()).isNull();
   }

   @Test
   void tagsInDesignOrder() {
      assertThat(VoiceParser.reply("Б/у, контрактные, из Японии, пара").tags())
            .containsExactly("Пара", "Япония", "Контрактные");
      assertThat(VoiceParser.reply("Есть новые японские").tags()).isEqualTo(List.of("Япония"));
   }

   @Test
   void offerFromDesign() {
      VoiceParser.OfferDraft draft = VoiceParser.offer(
            "Могу сегодня в три, от полутора тысяч. Диагностика бесплатно", NOW);
      assertThat(draft.priceFrom()).isEqualTo(1500);
      assertThat(draft.when()).isEqualTo(ServiceWhen.TODAY);
      assertThat(draft.availableAt()).isEqualTo(NOW.with(LocalTime.of(15, 0)).toInstant());
   }

   @Test
   void offerInMinutes() {
      VoiceParser.OfferDraft draft = VoiceParser.offer("Приеду через 20 минут, от 1500", NOW);
      assertThat(draft.priceFrom()).isEqualTo(1500);
      assertThat(draft.when()).isEqualTo(ServiceWhen.NOW);
      assertThat(draft.availableAt()).isEqualTo(NOW.plusMinutes(20).toInstant());
   }

   @Test
   void offerTomorrow() {
      VoiceParser.OfferDraft draft = VoiceParser.offer("завтра в десять утра, от тысячи двухсот", NOW);
      assertThat(draft.priceFrom()).isEqualTo(1200);
      assertThat(draft.when()).isEqualTo(ServiceWhen.TOMORROW);
      assertThat(draft.availableAt())
            .isEqualTo(ZonedDateTime.of(LocalDate.of(2026, 10, 8), LocalTime.of(10, 0), BISHKEK).toInstant());

      VoiceParser.OfferDraft noHour = VoiceParser.offer("завтра, от двух тысяч", NOW);
      assertThat(noHour.when()).isEqualTo(ServiceWhen.TOMORROW);
      assertThat(noHour.availableAt()).isEqualTo(NOW.plusDays(1).with(LocalTime.of(10, 0)).toInstant());
   }

   @Test
   void offerTimeIsNotPrice() {
      VoiceParser.OfferDraft draft = VoiceParser.offer("в 15:30 могу, две тысячи", NOW);
      assertThat(draft.priceFrom()).isEqualTo(2000);
      assertThat(draft.availableAt()).isEqualTo(NOW.with(LocalTime.of(15, 30)).toInstant());

      VoiceParser.OfferDraft now = VoiceParser.offer("сейчас приеду, от тысячи", NOW);
      assertThat(now.when()).isEqualTo(ServiceWhen.NOW);
      assertThat(now.priceFrom()).isEqualTo(1000);
   }
}
