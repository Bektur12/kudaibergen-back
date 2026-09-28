package kg.kudaibergen.catalog;

import java.util.List;

import kg.kudaibergen.catalog.dto.CarFilter;
import kg.kudaibergen.catalog.entity.Fitment;

/**
 * Правило «Подходит» (ТЗ 5.1) для выбранной машины, где модель и год могут быть не указаны
 * (поиск «по марке» с экрана 28). Та же логика, что в SQL {@link PartSearch}.
 */
public final class Fits {

   private Fits() {
   }

   public static boolean fits(Fitment fitment, CarFilter car) {
      return fitment.getBrandId().equals(car.brandId())
            && (car.modelId() == null || fitment.getModelId() == null || fitment.getModelId().equals(car.modelId()))
            && yearCovered(fitment, car.year());
   }

   /** Совпала именно модель — в выдаче первыми. */
   public static boolean exact(Fitment fitment, CarFilter car) {
      return car.modelId() != null && car.modelId().equals(fitment.getModelId()) && fits(fitment, car);
   }

   public static boolean anyFits(List<Fitment> fitments, CarFilter car) {
      return fitments.stream().anyMatch(fitment -> fits(fitment, car));
   }

   public static boolean anyExact(List<Fitment> fitments, CarFilter car) {
      return fitments.stream().anyMatch(fitment -> exact(fitment, car));
   }

   private static boolean yearCovered(Fitment fitment, Integer year) {
      return year == null
            || (fitment.getYearFrom() == null || year >= fitment.getYearFrom())
            && (fitment.getYearTo() == null || year <= fitment.getYearTo());
   }
}
