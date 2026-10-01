package kg.kudaibergen.ocr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Номера деталей из распознанного текста наклейки или коробки: «48510-06420», «90919 01253»,
 * «A 211 880 09 60», «31126771894». Кандидат — часть строки с цифрами или несколько таких частей подряд
 * (короткий буквенный префикс допускается), после нормализации 6–20 знаков и хотя бы 5 цифр.
 * Слова без цифр («KYB», «TOYOTA») номер не продолжают. Даты, телефоны и штрихкоды EAN-13
 * отбрасываются. Порядок — от самого похожего на номер.
 */
public final class OemExtractor {

   private static final int MAX_CANDIDATES = 5;
   /** Кириллические буквы, похожие на латинские: OCR часто путает их в номерах. */
   private static final String CYRILLIC = "АВЕКМНОРСТХУ";
   private static final String LATIN = "ABEKMHOPCTXY";
   private static final Pattern DATE = Pattern.compile("\\d{2}[.\\-/]\\d{2}[.\\-/]\\d{2,4}");
   private static final Pattern PHONE = Pattern.compile("^(996|0)\\d{9}$");

   private OemExtractor() {
   }

   public record Candidate(String display, String normalized) {
   }

   public static List<Candidate> extract(String text) {
      if (text == null || text.isBlank()) {
         return List.of();
      }
      Map<String, Candidate> unique = new LinkedHashMap<>();
      for (String line : text.split("\\R")) {
         List<String> run = new ArrayList<>();
         for (String raw : latinize(line.toUpperCase(Locale.ROOT)).split("\\s+")) {
            String token = raw.replaceAll("[^A-Z0-9.\\-/]", "");
            boolean hasDigit = token.chars().anyMatch(Character::isDigit);
            // номер — подряд идущие части с цифрами; короткий буквенный префикс («A 211 880…») — его начало
            boolean prefix = run.isEmpty() && token.matches("[A-Z]{1,2}");
            if (hasDigit || prefix) {
               run.add(token);
               if (hasDigit) {
                  add(unique, token);
               }
            } else {
               addRun(unique, run);
               run.clear();
            }
         }
         addRun(unique, run);
      }
      List<Candidate> candidates = new ArrayList<>(unique.values());
      candidates.sort(Comparator.comparingInt(OemExtractor::score).reversed());
      return candidates.stream().limit(MAX_CANDIDATES).toList();
   }

   private static void addRun(Map<String, Candidate> unique, List<String> run) {
      if (run.size() > 1) {
         add(unique, String.join(" ", run));
      }
   }

   private static void add(Map<String, Candidate> unique, String display) {
      if (DATE.matcher(display).matches()) {
         return;
      }
      String normalized = display.replaceAll("[^A-Z0-9]", "");
      if (looksLikeOem(normalized)) {
         unique.putIfAbsent(normalized, new Candidate(display, normalized));
      }
   }

   static boolean looksLikeOem(String normalized) {
      long digits = normalized.chars().filter(Character::isDigit).count();
      boolean ean13 = normalized.length() == 13 && digits == 13;
      return normalized.length() >= 6 && normalized.length() <= 20 && digits >= 5 && !ean13
            && !PHONE.matcher(normalized).matches();
   }

   /** Больше цифр и типичная длина 10–11 знаков (Toyota, BMW) — выше. */
   private static int score(Candidate candidate) {
      String value = candidate.normalized();
      int digits = (int) value.chars().filter(Character::isDigit).count();
      int lengthBonus = value.length() >= 9 && value.length() <= 12 ? 5 : 0;
      int separatorBonus = candidate.display().matches(".*\\d-\\d.*") ? 2 : 0;
      return digits + lengthBonus + separatorBonus;
   }

   private static String latinize(String text) {
      StringBuilder out = new StringBuilder(text.length());
      for (char c : text.toCharArray()) {
         int index = CYRILLIC.indexOf(c);
         out.append(index >= 0 ? LATIN.charAt(index) : c);
      }
      return out.toString();
   }
}
