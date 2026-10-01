package kg.kudaibergen.catalog;

import kg.kudaibergen.catalog.dto.CarFilter;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.UnauthorizedException;
import kg.kudaibergen.garage.GarageService;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.Car;
import kg.kudaibergen.garage.entity.CarModel;
import org.springframework.stereotype.Component;

/** «Ищу для машины»: машина из гаража (carId) или марка → модель → год без гаража (экран 28). */
@Component
public class CarFilters {

   private final GarageService garage;
   private final VehicleDirectory directory;

   public CarFilters(GarageService garage, VehicleDirectory directory) {
      this.garage = garage;
      this.directory = directory;
   }

   /** null — машина не выбрана. */
   public CarFilter resolve(Long userId, Long carId, Long brandId, Long modelId, Integer year) {
      if (carId != null) {
         if (userId == null) {
            throw new UnauthorizedException("UNAUTHORIZED", "Войдите, чтобы искать для машины из гаража");
         }
         Car car = garage.getOwned(carId, userId);
         return new CarFilter(car.getBrand().getId(), car.getModel().getId(), (int) car.getYear(),
               car.getModel().label() + " · " + car.getYear());
      }
      if (brandId == null) {
         if (modelId != null || year != null) {
            throw new BadRequestException("BRAND_REQUIRED", "Выберите марку");
         }
         return null;
      }
      Brand brand = directory.brand(brandId);
      if (modelId == null) {
         return new CarFilter(brandId, null, year, year == null ? brand.getName() : brand.getName() + " · " + year);
      }
      CarModel model = directory.model(modelId);
      if (!model.getBrandId().equals(brandId)) {
         throw new BadRequestException("MODEL_MISMATCH", "Модель не относится к выбранной марке");
      }
      return new CarFilter(brandId, modelId, year, year == null ? model.label() : model.label() + " · " + year);
   }
}
