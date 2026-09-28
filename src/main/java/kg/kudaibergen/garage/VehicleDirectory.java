package kg.kudaibergen.garage;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import org.springframework.stereotype.Service;

/**
 * Справочник марок и моделей в памяти: это сотни строк, меняются редко (админка вызовет
 * {@link #reload()}), а поиск «мерс», «бэха», «камри 50» удобнее и быстрее сделать здесь, чем в SQL.
 */
@Service
public class VehicleDirectory {

   /** Сначала популярные по sort_order, потом остальные по алфавиту (экраны 23, 28). */
   private static final Comparator<Brand> BRAND_ORDER = Comparator
         .comparing((Brand brand) -> brand.isPopular() ? 0 : 1)
         .thenComparing(Brand::getSortOrder)
         .thenComparing(brand -> brand.getName().toLowerCase(Locale.ROOT));

   private static final Comparator<CarModel> MODEL_ORDER = Comparator
         .comparing(CarModel::getSortOrder)
         .thenComparing(CarModel::getName)
         .thenComparing(model -> model.getGeneration() == null ? "" : model.getGeneration());

   private final BrandRepository brands;
   private final CarModelRepository models;
   private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

   public VehicleDirectory(BrandRepository brands, CarModelRepository models) {
      this.brands = brands;
      this.models = models;
   }

   public List<Brand> brands() {
      return current().brands;
   }

   public Brand brand(Long id) {
      return Optional.ofNullable(current().brandsById.get(id))
            .orElseThrow(() -> new NotFoundException("BRAND_NOT_FOUND", "Марка не найдена"));
   }

   public CarModel model(Long id) {
      return Optional.ofNullable(current().modelsById.get(id))
            .orElseThrow(() -> new NotFoundException("MODEL_NOT_FOUND", "Модель не найдена"));
   }

   public List<CarModel> modelsOf(Long brandId) {
      brand(brandId);
      return current().modelsByBrand.getOrDefault(brandId, List.of());
   }

   /**
    * Марки по строке поиска: совпала сама марка («мерс») или любая её модель («камри» → Toyota).
    * Пустой запрос — все марки.
    */
   public List<Brand> searchBrands(String query) {
      List<String> tokens = tokens(query);
      if (tokens.isEmpty()) {
         return brands();
      }
      Snapshot current = current();
      Set<Long> byModel = current.models.stream()
            .filter(model -> matchesAll(tokens, current.modelWords.get(model.getId())))
            .map(CarModel::getBrandId)
            .collect(Collectors.toSet());
      return current.brands.stream()
            .filter(brand -> matchesAll(tokens, current.brandWords.get(brand.getId()))
                  || byModel.contains(brand.getId()))
            .toList();
   }

   /** Модели по строке («камри 50», «мерс w211», «e класс»), при необходимости внутри марки. */
   public List<CarModel> searchModels(String query, Long brandId, int limit) {
      List<String> tokens = tokens(query);
      Snapshot current = current();
      Stream<CarModel> candidates = brandId == null ? current.models.stream() : modelsOf(brandId).stream();
      return candidates
            .filter(model -> tokens.isEmpty() || matchesAll(tokens, current.modelWords.get(model.getId())))
            .limit(limit)
            .toList();
   }

   /** Перечитать справочник из БД (после правок в админке). */
   public void reload() {
      snapshot.set(load());
   }

   private Snapshot current() {
      Snapshot loaded = snapshot.get();
      if (loaded == null) {
         loaded = load();
         snapshot.compareAndSet(null, loaded);
      }
      return loaded;
   }

   private Snapshot load() {
      List<Brand> allBrands = brands.findAll().stream().sorted(BRAND_ORDER).toList();
      Map<Long, Brand> brandsById = allBrands.stream().collect(Collectors.toMap(Brand::getId, Function.identity()));
      Map<Long, Integer> brandRank = new java.util.HashMap<>();
      for (int i = 0; i < allBrands.size(); i++) {
         brandRank.put(allBrands.get(i).getId(), i);
      }

      List<CarModel> allModels = models.findAll().stream()
            .sorted(Comparator.comparing((CarModel model) -> brandRank.getOrDefault(model.getBrandId(), 0))
                  .thenComparing(MODEL_ORDER))
            .toList();
      Map<Long, CarModel> modelsById = allModels.stream()
            .collect(Collectors.toMap(CarModel::getId, Function.identity()));
      Map<Long, List<CarModel>> modelsByBrand = allModels.stream()
            .collect(Collectors.groupingBy(CarModel::getBrandId));

      Map<Long, Set<String>> brandWords = allBrands.stream()
            .collect(Collectors.toMap(Brand::getId, VehicleDirectory::wordsOf));
      Map<Long, Set<String>> modelWords = allModels.stream().collect(Collectors.toMap(CarModel::getId, model -> {
         Set<String> words = new HashSet<>(brandWords.getOrDefault(model.getBrandId(), Set.of()));
         words.addAll(words(model.getName()));
         words.addAll(words(model.getGeneration()));
         model.getAliases().forEach(alias -> words.addAll(words(alias)));
         return words;
      }));
      return new Snapshot(allBrands, brandsById, allModels, modelsById, modelsByBrand, brandWords, modelWords);
   }

   private static Set<String> wordsOf(Brand brand) {
      Set<String> words = new HashSet<>(words(brand.getName()));
      words.addAll(words(brand.getSlug()));
      brand.getAliases().forEach(alias -> words.addAll(words(alias)));
      return words;
   }

   /** Слова для поиска: по отдельности и склеенные («CR-V» → «cr», «v», «crv»). */
   static Set<String> words(String text) {
      Set<String> words = new HashSet<>();
      if (text == null) {
         return words;
      }
      String normalized = normalize(text);
      Arrays.stream(normalized.split(" ")).filter(word -> !word.isEmpty()).forEach(words::add);
      String glued = normalized.replace(" ", "");
      if (!glued.isEmpty()) {
         words.add(glued);
      }
      return words;
   }

   static List<String> tokens(String query) {
      if (query == null) {
         return List.of();
      }
      return Arrays.stream(normalize(query).split(" ")).filter(token -> !token.isEmpty()).toList();
   }

   /** Нижний регистр, ё → е, всё кроме букв и цифр — пробел. */
   static String normalize(String text) {
      String lower = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
      StringBuilder out = new StringBuilder(lower.length());
      for (int i = 0; i < lower.length(); i++) {
         char c = lower.charAt(i);
         out.append(Character.isLetterOrDigit(c) ? c : ' ');
      }
      return out.toString().trim().replaceAll(" +", " ");
   }

   /** Каждое слово запроса — начало какого-то слова марки/модели. */
   private static boolean matchesAll(List<String> tokens, Set<String> words) {
      if (words == null) {
         return false;
      }
      return tokens.stream().allMatch(token -> words.stream().anyMatch(word -> word.startsWith(token)));
   }

   private record Snapshot(List<Brand> brands, Map<Long, Brand> brandsById, List<CarModel> models,
                           Map<Long, CarModel> modelsById, Map<Long, List<CarModel>> modelsByBrand,
                           Map<Long, Set<String>> brandWords, Map<Long, Set<String>> modelWords) {
   }
}
