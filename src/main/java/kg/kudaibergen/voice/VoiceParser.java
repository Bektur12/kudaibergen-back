package kg.kudaibergen.voice;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import kg.kudaibergen.master.entity.ServiceWhen;
import kg.kudaibergen.request.entity.PartCondition;

/**
 * Поля ответа из того, что сказал продавец или мастер (14, 19): «Есть, оригинал KYB, новые, пара — три двести» →
 * 3 200 сом, новое, оригинал, KYB, «Пара»; «Могу сегодня в три, от полутора тысяч» → от 1 500, сегодня 15:00.
 * Что не поняли — null: продавец выберет сам.
 */
public final class VoiceParser {

   /** Ответ «Есть» из голоса. manufacturer — «KYB» в чипе «Оригинал KYB», tags — остальные чипы. */
   public record ReplyDraft(Integer price, PartCondition condition, Boolean original, String manufacturer,
                            List<String> tags) {
   }

   /** Отклик мастера из голоса: цена «от», когда (Сейчас / Сегодня / Завтра) и точное время, если сказали. */
   public record OfferDraft(Integer priceFrom, ServiceWhen when, Instant availableAt) {
   }

   static final int MIN_PRICE = 50;
   static final int MAX_PRICE = 10_000_000;
   /** «Завтра» без часа — с утра. */
   static final LocalTime TOMORROW_DEFAULT = LocalTime.of(10, 0);

   private static final Pattern USED = pattern(
         "\\bб\\s*/\\s*у\\b|\\bб\\.\\s*у\\b|\\bбу\\b|\\bбэу\\b|\\bбэ\\s+у\\b|\\bбывш\\p{L}*|\\bконтрактн\\p{L}*"
               + "|\\bс\\s+разбора\\b|\\bразборк?\\p{L}*|\\bколдонулган\\p{L}*");
   private static final Pattern NEW = pattern("\\bнов(ые|ая|ое|ый|ую|ых|енькие)\\b|\\bжаңы\\p{L}*");
   private static final Pattern ON_ORDER = pattern("\\b(под|на)\\s+заказ\\p{L}*|\\bзакаж\\p{L}*|\\bзаказать\\b");
   private static final Pattern COPY = pattern(
         "\\bне\\s+оригинал\\p{L}*|\\bнеоригинал\\p{L}*|\\bкопи(я|ю|и)\\b|\\bаналог\\p{L}*|\\bреплик\\p{L}*");
   private static final Pattern ORIGINAL = pattern("\\bориг\\p{L}*|\\bродн(ые|ой|ая|ое)\\b");

   /** Производители — как пишутся в чипе. Ключ — как его могут распознать. */
   private static final Map<String, String> MANUFACTURERS = new LinkedHashMap<>();
   /** Чипы «Пара», страна и «Контрактные» — в этом порядке. */
   private static final Map<Pattern, String> TAGS = new LinkedHashMap<>();

   static {
      String[][] manufacturers = {
            {"KYB", "kyb", "кайба", "каяба", "kayaba", "кйб", "кеуби"},
            {"Denso", "denso", "денсо"},
            {"Bosch", "bosch", "бош"},
            {"NGK", "ngk", "нгк"},
            {"Aisin", "aisin", "айсин"},
            {"Tokico", "tokico", "токико"},
            {"Koito", "koito", "който"},
            {"Stanley", "stanley", "стенли", "стэнли"},
            {"Depo", "depo", "дэпо", "депо"},
            {"TYC", "tyc"},
            {"Hella", "hella", "хелла"},
            {"Valeo", "valeo", "валео"},
            {"Sachs", "sachs", "закс", "сакс"},
            {"Monroe", "monroe", "монро"},
            {"Lemforder", "lemforder", "лемфордер"},
            {"TRW", "trw"},
            {"Brembo", "brembo", "брембо"},
            {"Akebono", "akebono", "акебоно"},
            {"Sankei", "sankei", "санкей"},
            {"555", "555"},
            {"CTR", "ctr"},
            {"GMB", "gmb"},
            {"Febest", "febest", "фебест"},
            {"Febi", "febi", "феби"},
            {"Mann", "mann"},
            {"Mahle", "mahle", "мале"},
            {"Mobis", "mobis", "мобис"},
            {"Hyundai Mobis", "hyundai mobis"},
      };
      for (String[] row : manufacturers) {
         for (int i = 1; i < row.length; i++) {
            MANUFACTURERS.put(row[i], row[0]);
         }
      }
      TAGS.put(pattern("\\bпар(а|у|ой)\\b|\\bкомплект\\p{L}*|\\bжуп\\p{L}*"), "Пара");
      TAGS.put(pattern("\\bяпон\\p{L}*|\\bжапон\\p{L}*"), "Япония");
      TAGS.put(pattern("\\bкоре\\p{L}*"), "Корея");
      TAGS.put(pattern("\\bевроп\\p{L}*|\\bгерман\\p{L}*"), "Европа");
      TAGS.put(pattern("\\bамерик\\p{L}*|\\bсша\\b"), "США");
      TAGS.put(pattern("\\bкита(й|я|йск\\p{L}*)\\b"), "Китай");
      TAGS.put(pattern("\\bэмират\\p{L}*|\\bдуба\\p{L}*|\\bоаэ\\b"), "ОАЭ");
      TAGS.put(pattern("\\bконтрактн\\p{L}*"), "Контрактные");
   }

   /** После числа — не цена: «две штуки», «20 минут», «2012 года». */
   private static final Set<String> NOT_PRICE_AFTER = Set.of("шт", "штук", "штуки", "штука", "пар", "пары",
         "комплекта", "комплектов", "год", "года", "году", "годов", "лет", "мин", "минут", "минуты", "минуту", "час",
         "часа", "часов", "сек", "секунд", "км", "метров", "м", "мүнөт", "саат", "даана");
   private static final Set<String> CURRENCY = Set.of("сом", "сомов", "сома", "с", "руб", "рублей", "сомго");

   private VoiceParser() {
   }

   /** \\b и \\p{L} по-юникодному: иначе граница слова у кириллицы не находится. */
   private static Pattern pattern(String regex) {
      return Pattern.compile(regex, Pattern.UNICODE_CHARACTER_CLASS);
   }

   // ─────────────────────── ответ продавца ───────────────────────

   public static ReplyDraft reply(String transcript) {
      if (transcript == null || transcript.isBlank()) {
         return new ReplyDraft(null, null, null, null, List.of());
      }
      String text = normalize(transcript);
      List<String> tokens = SpokenNumbers.tokens(text);
      Integer price = price(tokens, new boolean[tokens.size()], false);
      return new ReplyDraft(price, condition(text), original(text), manufacturer(text), tags(text));
   }

   static PartCondition condition(String text) {
      if (ON_ORDER.matcher(text).find()) {
         return PartCondition.ON_ORDER;
      }
      if (USED.matcher(text).find()) {
         return PartCondition.USED;
      }
      return NEW.matcher(text).find() ? PartCondition.NEW : null;
   }

   static Boolean original(String text) {
      if (COPY.matcher(text).find()) {
         return false;
      }
      return ORIGINAL.matcher(text).find() ? true : null;
   }

   static String manufacturer(String text) {
      for (Map.Entry<String, String> entry : MANUFACTURERS.entrySet()) {
         if (pattern("(?<![\\p{L}\\d])" + Pattern.quote(entry.getKey()) + "(?![\\p{L}\\d])").matcher(text).find()) {
            return entry.getValue();
         }
      }
      return null;
   }

   static List<String> tags(String text) {
      List<String> tags = new ArrayList<>();
      TAGS.forEach((pattern, tag) -> {
         if (pattern.matcher(text).find() && !tags.contains(tag)) {
            tags.add(tag);
         }
      });
      return tags;
   }

   // ─────────────────────── отклик мастера ───────────────────────

   /** now — сейчас по Бишкеку: от него считаются «через 20 минут», «сегодня в три», «завтра». */
   public static OfferDraft offer(String transcript, ZonedDateTime now) {
      if (transcript == null || transcript.isBlank()) {
         return new OfferDraft(null, null, null);
      }
      List<String> tokens = SpokenNumbers.tokens(normalize(transcript));
      boolean[] used = new boolean[tokens.size()];
      When when = when(tokens, used, now);
      Integer price = price(tokens, used, true);
      return new OfferDraft(price, when.when(), when.at() == null ? null : when.at().toInstant());
   }

   private record When(ServiceWhen when, ZonedDateTime at) {
   }

   /** Время отклика; слова, ушедшие на время, помечаются в used, чтобы не стать ценой. */
   private static When when(List<String> tokens, boolean[] used, ZonedDateTime now) {
      LocalDate day = null;
      ZonedDateTime relative = null;
      boolean immediately = false;
      LocalTime clock = null;
      for (int i = 0; i < tokens.size(); i++) {
         String token = tokens.get(i);
         switch (token) {
            case "сегодня", "бүгүн" -> day = now.toLocalDate();
            case "завтра", "эртең" -> day = now.toLocalDate().plusDays(1);
            case "послезавтра" -> day = now.toLocalDate().plusDays(2);
            case "сейчас", "сразу", "азыр", "щас" -> immediately = true;
            case "полчаса" -> {
               relative = now.plusMinutes(30);
               used[i] = true;
            }
            case "через" -> {
               ZonedDateTime after = after(tokens, used, i + 1, now);
               if (after != null) {
                  relative = after;
                  used[i] = true;
               }
            }
            case "в", "к", "саат" -> {
               LocalTime at = clockAt(tokens, used, i + 1);
               if (at != null) {
                  clock = at;
                  used[i] = true;
               }
            }
            default -> {
               if (token.contains(":")) {
                  String[] parts = token.split(":");
                  clock = LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                  used[i] = true;
               }
            }
         }
      }
      if (relative != null) {
         return new When(relative.toLocalDate().equals(now.toLocalDate())
               ? Duration.between(now, relative).toMinutes() <= 60 ? ServiceWhen.NOW : ServiceWhen.TODAY
               : ServiceWhen.TOMORROW, relative);
      }
      if (clock != null) {
         LocalDate date = day != null ? day : now.toLocalDate();
         ZonedDateTime at = ZonedDateTime.of(date, clock, now.getZone());
         if (day == null && at.isBefore(now)) {
            at = at.plusDays(1);
         }
         return new When(at.toLocalDate().equals(now.toLocalDate()) ? ServiceWhen.TODAY : ServiceWhen.TOMORROW, at);
      }
      if (day != null && day.isAfter(now.toLocalDate())) {
         return new When(ServiceWhen.TOMORROW, ZonedDateTime.of(day, TOMORROW_DEFAULT, now.getZone()));
      }
      if (immediately) {
         return new When(ServiceWhen.NOW, now);
      }
      return day != null ? new When(ServiceWhen.TODAY, null) : new When(null, null);
   }

   /** «через 20 минут», «через час», «через два часа», «через полтора часа». */
   private static ZonedDateTime after(List<String> tokens, boolean[] used, int from, ZonedDateTime now) {
      if (from >= tokens.size()) {
         return null;
      }
      String first = tokens.get(from);
      if (first.startsWith("час") || first.equals("саат")) {
         used[from] = true;
         return now.plusHours(1);
      }
      if (first.equals("полчаса")) {
         used[from] = true;
         return now.plusMinutes(30);
      }
      List<SpokenNumbers.Phrase> phrases = SpokenNumbers.find(tokens.subList(from, tokens.size()),
            new boolean[tokens.size() - from]);
      if (phrases.isEmpty() || phrases.get(0).start() != 0) {
         return null;
      }
      SpokenNumbers.Phrase amount = phrases.get(0);
      int unitIndex = from + amount.end();
      if (unitIndex >= tokens.size()) {
         return null;
      }
      String unit = tokens.get(unitIndex);
      long minutes;
      if (unit.startsWith("мин") || unit.startsWith("мүнөт")) {
         minutes = Math.round(amount.value());
      } else if (unit.startsWith("час") || unit.equals("саат")) {
         minutes = Math.round(amount.value() * 60);
      } else {
         return null;
      }
      if (minutes <= 0 || minutes > 48 * 60) {
         return null;
      }
      for (int i = from; i <= unitIndex; i++) {
         used[i] = true;
      }
      return now.plusMinutes(minutes);
   }

   /**
    * «в три» → 15:00 (мастер днём: с 1 до 7 — это после обеда), «в 10», «в десять утра», «в три тридцать»,
    * «в 15:00», «в семь вечера». Час — первое слово, минуты — следующие.
    */
   private static LocalTime clockAt(List<String> tokens, boolean[] used, int from) {
      if (from >= tokens.size()) {
         return null;
      }
      String first = tokens.get(from);
      if (first.contains(":")) {
         String[] parts = first.split(":");
         used[from] = true;
         return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
      }
      Integer hour = SpokenNumbers.small(first);
      if (hour == null || hour < 0 || hour > 23) {
         return null;
      }
      int next = from + 1;
      int minute = 0;
      if (next < tokens.size() && tokens.get(next).startsWith("час")) {
         next++;
      }
      if (next < tokens.size() && SpokenNumbers.small(tokens.get(next)) != null) {
         Integer tens = SpokenNumbers.small(tokens.get(next));
         int value = tens;
         int consumed = 1;
         if (tens >= 20 && tens % 10 == 0 && next + 1 < tokens.size()) {
            Integer units = SpokenNumbers.small(tokens.get(next + 1));
            if (units != null && units > 0 && units < 10) {
               value += units;
               consumed = 2;
            }
         }
         if (value < 60 && (value >= 10 || tokens.get(next).equals("ноль"))) {
            minute = value;
            next += consumed;
         }
      }
      boolean morning = false;
      boolean evening = false;
      if (next < tokens.size()) {
         String part = tokens.get(next);
         morning = part.equals("утра");
         evening = part.equals("вечера") || part.equals("дня") || part.equals("кечки");
      }
      if (evening && hour < 12) {
         hour += 12;
      } else if (!morning && hour >= 1 && hour <= 7) {
         hour += 12;
      }
      for (int i = from; i < next; i++) {
         used[i] = true;
      }
      return LocalTime.of(hour % 24, minute);
   }

   // ─────────────────────── цена ───────────────────────

   /**
    * Цена: число со словом «сом» после него, иначе (у мастера) после «от», иначе последнее число от 50 сом.
    * Числа рядом со «штук», «минут», «года» — не цена. «Полторы» и «две с половиной» без «тысяч» — тысячи.
    */
   private static Integer price(List<String> tokens, boolean[] used, boolean fromPreferred) {
      List<SpokenNumbers.Phrase> phrases = SpokenNumbers.find(tokens, used);
      Integer withCurrency = null;
      Integer afterFrom = null;
      Integer last = null;
      for (SpokenNumbers.Phrase phrase : phrases) {
         String next = phrase.end() < tokens.size() ? tokens.get(phrase.end()) : "";
         if (NOT_PRICE_AFTER.contains(next)) {
            continue;
         }
         double value = phrase.value();
         if (value < 10 && value != Math.rint(value)) {
            value *= 1000;
         }
         long rounded = Math.round(value);
         if (rounded < MIN_PRICE || rounded > MAX_PRICE) {
            continue;
         }
         int price = (int) rounded;
         if (CURRENCY.contains(next)) {
            withCurrency = price;
         }
         if (phrase.start() > 0 && tokens.get(phrase.start() - 1).equals("от")) {
            afterFrom = price;
         }
         last = price;
      }
      if (withCurrency != null) {
         return withCurrency;
      }
      return fromPreferred && afterFrom != null ? afterFrom : last;
   }

   private static String normalize(String transcript) {
      return transcript.toLowerCase(Locale.ROOT).replace('ё', 'е');
   }
}
