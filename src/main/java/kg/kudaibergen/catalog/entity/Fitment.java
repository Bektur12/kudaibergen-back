package kg.kudaibergen.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * «Подходит к машине» (ТЗ 9.3): марка обязательна, модель null — «Все модели», годы null — без ограничения.
 * Правило «Подходит» (ТЗ 5.1) — {@link #fits(Long, Long, int)}.
 */
@Embeddable
public class Fitment {

   @Column(name = "brand_id", nullable = false)
   private Long brandId;

   @Column(name = "model_id")
   private Long modelId;

   @Column(name = "year_from")
   private Short yearFrom;

   @Column(name = "year_to")
   private Short yearTo;

   protected Fitment() {
   }

   public Fitment(Long brandId, Long modelId, Integer yearFrom, Integer yearTo) {
      this.brandId = brandId;
      this.modelId = modelId;
      this.yearFrom = yearFrom == null ? null : yearFrom.shortValue();
      this.yearTo = yearTo == null ? null : yearTo.shortValue();
   }

   /** Та же марка, модель не указана или совпадает, год машины в диапазоне (или годы не указаны). */
   public boolean fits(Long carBrandId, Long carModelId, int carYear) {
      return brandId.equals(carBrandId)
            && (modelId == null || modelId.equals(carModelId))
            && (yearFrom == null || carYear >= yearFrom)
            && (yearTo == null || carYear <= yearTo);
   }

   /** Совпала именно модель — такие в выдаче первыми. */
   public boolean fitsExactly(Long carBrandId, Long carModelId, int carYear) {
      return modelId != null && fits(carBrandId, carModelId, carYear);
   }

   public Long getBrandId() {
      return brandId;
   }

   public Long getModelId() {
      return modelId;
   }

   public Integer getYearFrom() {
      return yearFrom == null ? null : yearFrom.intValue();
   }

   public Integer getYearTo() {
      return yearTo == null ? null : yearTo.intValue();
   }
}
