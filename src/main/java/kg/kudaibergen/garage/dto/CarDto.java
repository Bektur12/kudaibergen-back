package kg.kudaibergen.garage.dto;

import java.math.BigDecimal;

import kg.kudaibergen.garage.CarOrigins;
import kg.kudaibergen.garage.entity.Car;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.garage.entity.FuelType;
import org.springframework.lang.Nullable;

/**
 * Машина из гаража. Готовые подписи из макета:
 * title «Toyota Camry 50» (или своё название), subtitle «2012 · 2.5 бензин», label «Camry 50 · 2012» (чипы 06, 27).
 */
public record CarDto(Long id, BrandDto brand, ModelDto model, int year, @Nullable String engine, @Nullable String vin, @Nullable String name,
                     boolean isPrimary, String title, String subtitle, String label, @Nullable BigDecimal engineVolume,
                     @Nullable FuelType fuel, CarOrigin origin) {

   public static CarDto of(Car car) {
      String modelLabel = car.getModel().label();
      String title = car.getName() != null ? car.getName() : car.getBrand().getName() + " " + modelLabel;
      String subtitle = car.getEngine() == null ? String.valueOf(car.getYear()) : car.getYear() + " · " + car.getEngine();
      return new CarDto(car.getId(), BrandDto.of(car.getBrand()), ModelDto.of(car.getModel()), car.getYear(),
            car.getEngine(), car.getVin(), car.getName(), car.isPrimary(), title, subtitle,
            modelLabel + " · " + car.getYear(), car.getEngineVolume(), car.getFuel(),
            car.getOrigin() != null ? car.getOrigin() : CarOrigins.of(car.getBrand().getSlug()));
   }
}
