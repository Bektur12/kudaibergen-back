package kg.kudaibergen.garage.dto;

import java.math.BigDecimal;

import kg.kudaibergen.garage.CarOrigins;
import kg.kudaibergen.garage.entity.Car;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.garage.entity.FuelType;
import org.springframework.lang.Nullable;

/**
 * Машина из гаража. Готовые подписи из макета:
 * title «Toyota Camry 50» (или своё название), subtitle «2012 · 2.5 бензин», label «Camry 50 · 2012» (чипы 04, 16).
 * fromTechPassport — «Техпаспорт сфотан — продавцы видят точную модель» (10).
 */
public record CarDto(Long id, BrandDto brand, ModelDto model, int year, @Nullable String engine, @Nullable String vin, @Nullable String name,
                     boolean isPrimary, String title, String subtitle, String label, @Nullable BigDecimal engineVolume,
                     @Nullable FuelType fuel, CarOrigin origin, boolean fromTechPassport) {

   public static CarDto of(Car car) {
      String modelLabel = car.getModel().label();
      String title = car.getName() != null ? car.getName() : car.getBrand().getName() + " " + modelLabel;
      String engine = car.getEngine() != null ? car.getEngine() : engineLabel(car.getEngineVolume(), car.getFuel());
      String subtitle = engine == null ? String.valueOf(car.getYear()) : car.getYear() + " · " + engine;
      return new CarDto(car.getId(), BrandDto.of(car.getBrand()), ModelDto.of(car.getModel()), car.getYear(),
            car.getEngine(), car.getVin(), car.getName(), car.isPrimary(), title, subtitle,
            modelLabel + " · " + car.getYear(), car.getEngineVolume(), car.getFuel(),
            car.getOrigin() != null ? car.getOrigin() : CarOrigins.of(car.getBrand().getSlug()),
            car.isFromTechPassport());
   }

   /** «2.5 бензин» из объёма и топлива, если двигатель не подписан. */
   static String engineLabel(BigDecimal volume, FuelType fuel) {
      String fuelLabel = fuel == null ? null : switch (fuel) {
         case PETROL -> "бензин";
         case DIESEL -> "дизель";
         case LPG_PETROL -> "газ/бензин";
         case HYBRID -> "гибрид";
         case ELECTRIC -> "электро";
      };
      String volumeLabel = volume == null ? null : volume.stripTrailingZeros().toPlainString();
      if (volumeLabel == null) {
         return fuelLabel;
      }
      return fuelLabel == null ? volumeLabel : volumeLabel + " " + fuelLabel;
   }
}
