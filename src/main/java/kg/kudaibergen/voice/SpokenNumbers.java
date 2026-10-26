package kg.kudaibergen.voice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Числа в распознанной речи — цифрами («3200», «3 200», «3-200») и словами по-русски и по-кыргызски:
 * «три тысячи двести», «три двести» (на рынке — 3 200), «полторы тысячи», «от полутора тысяч», «две с половиной»,
 * «үч миң эки жүз». Текст режется на слова; {@link #find} собирает подряд идущие числовые слова в одно число.
 */
final class SpokenNumbers {

   /** Число в тексте: value, слова [start, end). */
   record Phrase(double value, int start, int end) {
   }

   private static final Pattern TOKEN = Pattern.compile("\\p{L}+|\\d+(?:[.,]\\d+)?");
   /** «15:00», «15.30» — время цифрами одним словом. */
   private static final Pattern CLOCK = Pattern.compile("\\b([01]?\\d|2[0-3])[:.]([0-5]\\d)\\b");

   private static final Map<String, Integer> SMALL = new HashMap<>();
   private static final Map<String, Integer> HUNDREDS = new HashMap<>();
   private static final Set<String> THOUSAND = Set.of("тысяча", "тысячи", "тысяч", "тысячу", "тысяче", "тысячей", "тыс", "тыща",
         "тыщи", "тыщ", "тыщу", "косарь", "косаря", "косарей", "миң");
   /** Множитель сразу после числа, но не начало числа: «3к». */
   private static final Set<String> THOUSAND_SUFFIX = Set.of("к", "k");
   private static final Set<String> ONE_AND_HALF = Set.of("полторы", "полтора", "полутора");
   private static final String KG_HUNDRED = "жүз";
   private static final String KG_HALF = "жарым";

   static {
      String[][] small = {
            {"0", "ноль", "нуль"},
            {"1", "один", "одна", "одну", "одного", "одной", "бир"},
            {"2", "два", "две", "двух", "эки"},
            {"3", "три", "трех", "трем", "үч"},
            {"4", "четыре", "четырех", "төрт"},
            {"5", "пять", "пяти", "беш"},
            {"6", "шесть", "шести", "алты"},
            {"7", "семь", "семи", "жети"},
            {"8", "восемь", "восьми", "сегиз"},
            {"9", "девять", "девяти", "тогуз"},
            {"10", "десять", "десяти", "он"},
            {"11", "одиннадцать", "одиннадцати"},
            {"12", "двенадцать", "двенадцати"},
            {"13", "тринадцать", "тринадцати"},
            {"14", "четырнадцать", "четырнадцати"},
            {"15", "пятнадцать", "пятнадцати"},
            {"16", "шестнадцать", "шестнадцати"},
            {"17", "семнадцать", "семнадцати"},
            {"18", "восемнадцать", "восемнадцати"},
            {"19", "девятнадцать", "девятнадцати"},
            {"20", "двадцать", "двадцати", "жыйырма"},
            {"30", "тридцать", "тридцати", "отуз"},
            {"40", "сорок", "сорока", "кырк"},
            {"50", "пятьдесят", "пятидесяти", "элүү"},
            {"60", "шестьдесят", "шестидесяти", "алтымыш"},
            {"70", "семьдесят", "семидесяти", "жетимиш"},
            {"80", "восемьдесят", "восьмидесяти", "сексен"},
            {"90", "девяносто", "девяноста", "токсон"},
      };
      for (String[] row : small) {
         for (int i = 1; i < row.length; i++) {
            SMALL.put(row[i], Integer.parseInt(row[0]));
         }
      }
      String[][] hundreds = {
            {"100", "сто", "ста", "сотня"},
            {"200", "двести", "двухсот"},
            {"300", "триста", "трехсот"},
            {"400", "четыреста", "четырехсот"},
            {"500", "пятьсот", "пятисот"},
            {"600", "шестьсот", "шестисот"},
            {"700", "семьсот", "семисот"},
            {"800", "восемьсот", "восьмисот"},
            {"900", "девятьсот", "девятисот"},
      };
      for (String[] row : hundreds) {
         for (int i = 1; i < row.length; i++) {
            HUNDREDS.put(row[i], Integer.parseInt(row[0]));
         }
      }
   }

   private SpokenNumbers() {
   }

   /** Слова текста в нижнем регистре, «ё» → «е»; «15:00» остаётся одним словом. */
   static List<String> tokens(String text) {
      String normalized = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
      List<String> tokens = new ArrayList<>();
      Matcher clock = CLOCK.matcher(normalized);
      int from = 0;
      while (clock.find()) {
         addTokens(normalized.substring(from, clock.start()), tokens);
         tokens.add(clock.group(1) + ":" + clock.group(2));
         from = clock.end();
      }
      addTokens(normalized.substring(from), tokens);
      return tokens;
   }

   private static void addTokens(String part, List<String> tokens) {
      Matcher matcher = TOKEN.matcher(part);
      while (matcher.find()) {
         tokens.add(matcher.group());
      }
   }

   static boolean isDigits(String token) {
      return !token.isEmpty() && Character.isDigit(token.charAt(0)) && token.indexOf(':') < 0;
   }

   /** Одно слово — число от 0 до 99 («три», «двадцать», «15»), иначе null. */
   static Integer small(String token) {
      if (isDigits(token)) {
         try {
            double value = Double.parseDouble(token.replace(',', '.'));
            return value == Math.rint(value) && value < 100 ? (int) value : null;
         } catch (NumberFormatException e) {
            return null;
         }
      }
      return SMALL.get(token);
   }

   /** Все числа текста по порядку. used — слова, уже занятые (например, время), числа там не ищутся. */
   static List<Phrase> find(List<String> tokens, boolean[] used) {
      List<Phrase> phrases = new ArrayList<>();
      int i = 0;
      while (i < tokens.size()) {
         if (used[i] || !startsNumber(tokens.get(i))) {
            i++;
            continue;
         }
         int start = i;
         double total = 0;
         double current = 0;
         // в current только единицы и десятки: «три» перед «двести» — это тысячи («три двести» = 3 200)
         boolean unitsOnly = false;
         boolean any = false;
         while (i < tokens.size() && !used[i]) {
            String token = tokens.get(i);
            if (isDigits(token)) {
               if (any && !(unitsOnly && current < 1000)) {
                  break;
               }
               String digits = token.replace(',', '.');
               double value = Double.parseDouble(digits);
               // «3 200», «3-200»: тысячи и сотни разными словами
               if (!any && !digits.contains(".") && digits.length() <= 3 && i + 1 < tokens.size() && !used[i + 1]
                     && tokens.get(i + 1).length() == 3 && tokens.get(i + 1).chars().allMatch(Character::isDigit)) {
                  value = value * 1000 + Double.parseDouble(tokens.get(i + 1));
                  i++;
               }
               if (any) {
                  // «три 200» — так распознаватель пишет «три двести»
                  if (value % 100 == 0 && value < 1000) {
                     total += current * 1000;
                     current = value;
                  } else {
                     break;
                  }
               } else {
                  current = value;
               }
               unitsOnly = current < 100;
            } else if (SMALL.containsKey(token)) {
               int value = SMALL.get(token);
               // «двадцать пять» — да, «пять двадцать» или «десять три» — это два разных числа
               if (any && unitsOnly && !(current % 10 == 0 && current >= 20 && value < 10)) {
                  break;
               }
               current += value;
               unitsOnly = current < 100;
            } else if (HUNDREDS.containsKey(token)) {
               int value = HUNDREDS.get(token);
               if (unitsOnly && current > 0) {
                  total += current * 1000;
                  current = value;
               } else if (current % 1000 >= 100) {
                  break;
               } else {
                  current += value;
               }
               unitsOnly = false;
            } else if (token.equals(KG_HUNDRED)) {
               current = (current == 0 ? 1 : current) * 100;
               unitsOnly = false;
            } else if (ONE_AND_HALF.contains(token)) {
               if (any && current > 0) {
                  break;
               }
               current = 1.5;
               unitsOnly = true;
            } else if (token.equals(KG_HALF) && any) {
               current += 0.5;
            } else if (token.equals("с") && any && i + 1 < tokens.size() && tokens.get(i + 1).equals("половиной")) {
               current += 0.5;
               i++;
            } else if (THOUSAND.contains(token) || any && THOUSAND_SUFFIX.contains(token)) {
               total += (current == 0 ? 1 : current) * 1000;
               current = 0;
               unitsOnly = false;
            } else {
               break;
            }
            any = true;
            i++;
         }
         if (any) {
            phrases.add(new Phrase(total + current, start, i));
         } else {
            i = start + 1;
         }
      }
      return phrases;
   }

   static boolean startsNumber(String token) {
      return isDigits(token) || SMALL.containsKey(token) || HUNDREDS.containsKey(token)
            || ONE_AND_HALF.contains(token) || token.equals(KG_HUNDRED)
            || THOUSAND.contains(token);
   }
}
