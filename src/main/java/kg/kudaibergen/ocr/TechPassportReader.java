package kg.kudaibergen.ocr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.dto.ModelDto;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.garage.entity.FuelType;
import org.springframework.stereotype.Component;

/**
 * Свидетельство о регистрации ТС Кыргызстана (техпаспорт) — текст с фото в поля машины: «Марка, модель:
 * TOYOTA CAMRY», «Год выпуска: 2012», VIN, «Объём двигателя, см³: 2494», «Тип топлива: бензин».
 * Модель — из справочника марки, поколение — по году («Camry» 2012 → «Camry 50»).
 */
@Component
public class TechPassportReader {

   private static final Pattern VIN = Pattern.compile("\\b[A-HJ-NPR-Z0-9]{17}\\b");
   private static final Pattern YEAR_LABEL = Pattern.compile(
         "(год\\s+выпуска|чыгарылган\\s+жыл\\p{L}*|year)[^0-9]{0,40}((19|20)\\d{2})", Pattern.UNICODE_CHARACTER_CLASS);
   private static final Pattern ANY_YEAR = Pattern.compile("\\b((19|20)\\d{2})\\b");
   private static final Pattern VOLUME_LABEL = Pattern.compile(
         "(объем|объём|көлөм\\p{L}*|см3|см³|куб)[^0-9]{0,40}(\\d{3,4})", Pattern.UNICODE_CHARACTER_CLASS);

   private final VehicleDirectory directory;

   public TechPassportReader(VehicleDirectory directory) {
      this.directory = directory;
   }

   public TechPassportDto read(String text) {
      String upper = text.toUpperCase(Locale.ROOT);
      String lower = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
      Integer year = year(lower);
      Optional<Brand> brand = brand(upper);
      Optional<CarModel> model = brand.flatMap(found -> model(found, upper, year));
      return new TechPassportDto(brand.map(BrandDto::of).orElse(null), model.map(ModelDto::of).orElse(null), year,
            vin(upper), volume(lower), fuel(lower));
   }

   static String vin(String upper) {
      Matcher matcher = VIN.matcher(upper);
      while (matcher.find()) {
         String candidate = matcher.group();
         // в номере кузова и госномере есть буквы и цифры, в VIN — обязательно и то и другое
         if (candidate.chars().anyMatch(Character::isLetter) && candidate.chars().anyMatch(Character::isDigit)) {
            return candidate;
         }
      }
      return null;
   }

   static Integer year(String lower) {
      int max = Year.now().getValue() + 1;
      Matcher labeled = YEAR_LABEL.matcher(lower);
      if (labeled.find()) {
         int year = Integer.parseInt(labeled.group(2));
         if (year >= 1950 && year <= max) {
            return year;
         }
      }
      Matcher any = ANY_YEAR.matcher(lower);
      while (any.find()) {
         int year = Integer.parseInt(any.group(1));
         if (year >= 1950 && year <= max) {
            return year;
         }
      }
      return null;
   }

   /** «2494» см³ → 2.5 л. */
   static BigDecimal volume(String lower) {
      Matcher matcher = VOLUME_LABEL.matcher(lower);
      if (!matcher.find()) {
         return null;
      }
      int cc = Integer.parseInt(matcher.group(2));
      if (cc < 500 || cc > 9900) {
         return null;
      }
      return BigDecimal.valueOf(cc).divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP);
   }

   static FuelType fuel(String lower) {
      if (lower.contains("гибрид")) {
         return FuelType.HYBRID;
      }
      if (lower.contains("электр")) {
         return FuelType.ELECTRIC;
      }
      if (lower.contains("дизел")) {
         return FuelType.DIESEL;
      }
      if (lower.contains("газ") || lower.contains("пропан") || lower.contains("метан")) {
         return FuelType.LPG_PETROL;
      }
      return lower.contains("бензин") ? FuelType.PETROL : null;
   }

   /** Марка, чьё название или народное имя встречается в тексте раньше других. */
   private Optional<Brand> brand(String upper) {
      return directory.brands().stream()
            .map(brand -> new Found<>(brand, firstIndex(upper, brandNames(brand))))
            .filter(found -> found.index() >= 0)
            .min(Comparator.comparingInt(Found::index))
            .map(Found::item);
   }

   /** Модель марки по названию в тексте; из поколений — то, куда попадает год. */
   private Optional<CarModel> model(Brand brand, String upper, Integer year) {
      List<CarModel> named = directory.modelsOf(brand.getId()).stream()
            .filter(CarModel::isActive)
            .filter(model -> firstIndex(upper, modelNames(model)) >= 0)
            .toList();
      if (named.isEmpty()) {
         return Optional.empty();
      }
      // самое длинное совпавшее название: «Land Cruiser Prado», а не «Land Cruiser»
      int longest = named.stream().mapToInt(model -> model.getName().length()).max().orElse(0);
      List<CarModel> best = named.stream().filter(model -> model.getName().length() == longest).toList();
      return best.stream().filter(model -> year == null || model.covers(year)).findFirst()
            .or(() -> best.stream().findFirst());
   }

   private record Found<T>(T item, int index) {
   }

   private static List<String> brandNames(Brand brand) {
      return Stream.concat(Stream.of(brand.getName(), brand.getShortName()),
            brand.getAliases().stream()).filter(name -> name != null && name.length() >= 2).toList();
   }

   private static List<String> modelNames(CarModel model) {
      return Stream.concat(Stream.of(model.getName()), model.getAliases().stream())
            .filter(name -> name != null && name.length() >= 2).toList();
   }

   /** Первое вхождение любого из имён отдельным словом; -1 — нет. */
   static int firstIndex(String upper, List<String> names) {
      int best = -1;
      for (String name : names) {
         Matcher matcher = Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(name.toUpperCase(Locale.ROOT))
               + "(?![\\p{L}\\d])").matcher(upper);
         if (matcher.find() && (best < 0 || matcher.start() < best)) {
            best = matcher.start();
         }
      }
      return best;
   }
}
