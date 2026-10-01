package kg.kudaibergen.admin.dictionaries;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.master.entity.ServiceDuration;
import org.springframework.lang.Nullable;

/** Справочники [A5, A9]: марки и модели, категории, услуги, синонимы, подсказки. */
public final class AdminDictionaryDtos {

   static final String SLUG = "[a-z0-9][a-z0-9-]{1,39}";
   static final String COLOR = "#[0-9A-Fa-f]{6}";

   private AdminDictionaryDtos() {
   }

   // ─────────────── марки ───────────────

   /**
    * Марка с счётчиками: modelsCount — моделей, sellersCount — магазинов, которые её продают, mastersCount — мастеров,
    * которые с ней работают (без «все марки»), carsCount — машин в гаражах.
    */
   public record AdminBrandDto(Long id, String slug, String name, String shortName, @Nullable String logoUrl,
                               String placeholder, String color, boolean popular, int sortOrder, List<String> aliases,
                               boolean active, long modelsCount, long sellersCount, long mastersCount, long carsCount) {
   }

   public record BrandCounts(long all, long active, long hidden, long popular) {
   }

   public record CreateBrandRequest(
         @NotBlank @Pattern(regexp = SLUG, message = "slug — латиница, цифры и дефис") String slug,
         @NotBlank @Size(max = 60) String name,
         @NotBlank @Size(max = 30) String shortName,
         @NotBlank @Size(max = 4) String placeholder,
         @NotBlank @Pattern(regexp = COLOR, message = "Цвет в формате #RRGGBB") String color,
         boolean popular,
         @Min(0) @Max(1000) Integer sortOrder,
         List<@NotBlank @Size(max = 40) String> aliases) {
   }

   /** null — не менять. aliases — весь список заново («мерс, мерседес»). active = false — «Скрыть». */
   public record UpdateBrandRequest(
         @Size(min = 1, max = 60) String name,
         @Size(min = 1, max = 30) String shortName,
         @Size(min = 1, max = 4) String placeholder,
         @Pattern(regexp = COLOR, message = "Цвет в формате #RRGGBB") String color,
         Boolean popular,
         @Min(0) @Max(1000) Integer sortOrder,
         List<@NotBlank @Size(max = 40) String> aliases,
         Boolean active) {
   }

   /** Логотип: id фото, загруженного через POST /media/photos с purpose=BRAND. */
   public record BrandLogoRequest(@NotNull Long mediaId) {
   }

   /** Порядок перетаскиванием: id по порядку, sortOrder станет 1, 2, 3… */
   public record OrderRequest(@NotEmpty List<@NotNull Long> ids) {
   }

   // ─────────────── модели ───────────────

   /** label — подпись в приложении («Camry 50»); displayName — если задана вручную. */
   public record AdminModelDto(Long id, Long brandId, String name, @Nullable String generation,
                               @Nullable String displayName, String label, @Nullable Integer yearFrom,
                               @Nullable Integer yearTo, int sortOrder, List<String> aliases, boolean active,
                               long carsCount, long partsCount) {
   }

   public record ModelCounts(long all, long active, long hidden) {
   }

   public record CreateModelRequest(
         @NotBlank @Size(max = 60) String name,
         @Size(max = 30) String generation,
         @Size(max = 80) String displayName,
         @Min(1950) @Max(2100) Integer yearFrom,
         @Min(1950) @Max(2100) Integer yearTo,
         @Min(0) @Max(1000) Integer sortOrder,
         List<@NotBlank @Size(max = 40) String> aliases) {
   }

   /** null — не менять; пустая строка в generation / displayName — стереть. */
   public record UpdateModelRequest(
         @Size(min = 1, max = 60) String name,
         @Size(max = 30) String generation,
         @Size(max = 80) String displayName,
         @Min(1950) @Max(2100) Integer yearFrom,
         @Min(1950) @Max(2100) Integer yearTo,
         @Min(0) @Max(1000) Integer sortOrder,
         List<@NotBlank @Size(max = 40) String> aliases,
         Boolean active) {
   }

   // ─────────────── категории ───────────────

   public record AdminCategoryDto(Long id, String slug, String nameRu, String nameKg, int sortOrder, boolean active,
                                  long partsCount, long shopsCount) {
   }

   public record CategoryCounts(long all, long active, long hidden) {
   }

   public record CreateCategoryRequest(
         @NotBlank @Pattern(regexp = SLUG, message = "slug — латиница, цифры и дефис") String slug,
         @NotBlank @Size(max = 60) String nameRu,
         @NotBlank @Size(max = 60) String nameKg) {
   }

   public record UpdateCategoryRequest(@Size(min = 1, max = 60) String nameRu, @Size(min = 1, max = 60) String nameKg,
                                       Boolean active) {
   }

   // ─────────────── услуги ───────────────

   /**
    * Услуга [A9]: needsLocation — «точка на карте», defaultDuration — «ожидание» откликов по умолчанию,
    * urgent — метка «Срочно», mastersCount — мастеров с этой услугой, requests30d — заявок за 30 дней.
    */
   public record AdminServiceTypeDto(String code, String nameRu, String nameKg, String icon, int sortOrder,
                                     boolean needsLocation, boolean urgent, ServiceDuration defaultDuration,
                                     int defaultRadiusKm, boolean active, long mastersCount, long requests30d) {
   }

   public record ServiceTypeCounts(long all, long active, long hidden) {
   }

   public record CreateServiceTypeRequest(
         @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,19}", message = "Код — латиница в верхнем регистре") String code,
         @NotBlank @Size(max = 40) String nameRu,
         @NotBlank @Size(max = 40) String nameKg,
         @NotBlank @Size(max = 30) String icon,
         boolean needsLocation,
         boolean urgent,
         ServiceDuration defaultDuration,
         @Min(1) @Max(50) Integer defaultRadiusKm) {
   }

   public record UpdateServiceTypeRequest(
         @Size(min = 1, max = 40) String nameRu,
         @Size(min = 1, max = 40) String nameKg,
         @Size(min = 1, max = 30) String icon,
         Boolean needsLocation,
         Boolean urgent,
         ServiceDuration defaultDuration,
         @Min(1) @Max(50) Integer defaultRadiusKm,
         Boolean active) {
   }

   public record ServiceOrderRequest(@NotEmpty List<@NotBlank String> codes) {
   }

   // ─────────────── синонимы и подсказки ───────────────

   /** Пара для поиска: слово запроса term находит и synonym. */
   public record AdminSynonymDto(Long id, String term, String synonym) {
   }

   public record SynonymCounts(long all) {
   }

   /** bidirectional (по умолчанию) — сразу и обратная пара: «стойка ↔ амортизатор». */
   public record CreateSynonymRequest(@NotBlank @Size(max = 40) String term, @NotBlank @Size(max = 40) String synonym,
                                      Boolean bidirectional) {
   }

   /** Подсказка «Что ищем?» (06): popularity — базовый вес, пока по машине нет запросов. */
   public record AdminHintDto(Long id, String textRu, String textKg, @Nullable Long categoryId, int popularity,
                              boolean active, long requestsCount) {
   }

   public record HintCounts(long all, long active, long hidden) {
   }

   public record CreateHintRequest(@NotBlank @Size(max = 40) String textRu, @NotBlank @Size(max = 40) String textKg,
                                   Long categoryId, @Min(0) @Max(1000) Integer popularity) {
   }

   /** categoryId = 0 — убрать категорию. */
   public record UpdateHintRequest(@Size(min = 1, max = 40) String textRu, @Size(min = 1, max = 40) String textKg,
                                   Long categoryId, @Min(0) @Max(1000) Integer popularity, Boolean active) {
   }
}
