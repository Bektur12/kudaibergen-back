package kg.kudaibergen.garage;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import kg.kudaibergen.common.error.ApiException;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.garage.dto.CarRules;
import kg.kudaibergen.garage.dto.CreateCarRequest;
import kg.kudaibergen.garage.dto.UpdateCarRequest;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.Car;
import kg.kudaibergen.garage.entity.CarModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Гараж покупателя (экран 04): до 10 машин, одна «Основная» — она выбрана по умолчанию
 * в запросе (06) и каталоге (27). Первая машина становится основной сама.
 */
@Service
public class GarageService {

   private final CarRepository cars;
   private final VehicleDirectory directory;

   public GarageService(CarRepository cars, VehicleDirectory directory) {
      this.cars = cars;
      this.directory = directory;
   }

   @Transactional(readOnly = true)
   public List<Car> list(Long userId) {
      return cars.findGarage(userId);
   }

   /** Машина пользователя; чужая или несуществующая — 404 (не раскрываем, что она есть). */
   @Transactional(readOnly = true)
   public Car getOwned(Long carId, Long userId) {
      return cars.findByIdAndUserId(carId, userId)
            .orElseThrow(() -> new NotFoundException("CAR_NOT_FOUND", "Машина не найдена"));
   }

   @Transactional(readOnly = true)
   public Optional<Car> primary(Long userId) {
      return cars.findByUserIdAndPrimaryTrue(userId);
   }

   @Transactional
   public Car add(Long userId, CreateCarRequest request) {
      long count = cars.countByUserId(userId);
      if (count >= CarRules.MAX_CARS) {
         throw new ConflictException("GARAGE_LIMIT", "В гараже может быть не больше " + CarRules.MAX_CARS + " машин");
      }
      CarModel model = directory.model(request.modelId());
      short year = checkedYear(model, request.year());
      Brand brand = directory.brand(model.getBrandId());

      Car car = new Car(userId, brand, model, year);
      car.setEngine(blankToNull(request.engine()));
      car.setVin(normalizeVin(request.vin()));
      car.setName(blankToNull(request.name()));
      car.setEngineVolume(request.engineVolume());
      car.setFuel(request.fuel());
      car.setOrigin(request.origin() != null ? request.origin() : CarOrigins.of(brand.getSlug()));
      boolean makePrimary = count == 0 || Boolean.TRUE.equals(request.isPrimary());
      if (makePrimary) {
         cars.clearPrimary(userId);
      }
      car.setPrimary(makePrimary);
      return cars.save(car);
   }

   @Transactional
   public Car update(Long carId, Long userId, UpdateCarRequest request) {
      Car car = getOwned(carId, userId);
      CarModel model = request.modelId() == null ? car.getModel() : directory.model(request.modelId());
      int year = request.year() == null ? car.getYear() : request.year();
      if (request.modelId() != null || request.year() != null) {
         car.setYear(checkedYear(model, year));
         car.changeModel(directory.brand(model.getBrandId()), model);
      }
      if (request.engine() != null) {
         car.setEngine(blankToNull(request.engine()));
      }
      if (request.vin() != null) {
         car.setVin(normalizeVin(request.vin()));
      }
      if (request.name() != null) {
         car.setName(blankToNull(request.name()));
      }
      if (request.engineVolume() != null) {
         car.setEngineVolume(request.engineVolume());
      }
      if (request.fuel() != null) {
         car.setFuel(request.fuel());
      }
      if (request.origin() != null) {
         car.setOrigin(request.origin());
      }
      return car;
   }

   @Transactional
   public Car makePrimary(Long carId, Long userId) {
      Car car = getOwned(carId, userId);
      if (!car.isPrimary()) {
         cars.clearPrimary(userId);
         car.setPrimary(true);
      }
      return car;
   }

   /** Удалили основную — основной становится самая ранняя из оставшихся. */
   @Transactional
   public void delete(Long carId, Long userId) {
      Car car = getOwned(carId, userId);
      boolean wasPrimary = car.isPrimary();
      cars.delete(car);
      cars.flush();
      if (wasPrimary) {
         cars.findGarage(userId).stream().findFirst().ifPresent(next -> next.setPrimary(true));
      }
   }

   private static short checkedYear(CarModel model, int year) {
      if (!model.covers(year)) {
         ApiException error = new BadRequestException("YEAR_OUT_OF_RANGE",
               model.label() + " выпускалась " + range(model) + ", выберите год из этого диапазона");
         throw error.with("yearFrom", model.getYearFrom()).with("yearTo", model.getYearTo());
      }
      return (short) year;
   }

   private static String range(CarModel model) {
      String from = model.getYearFrom() == null ? "…" : String.valueOf(model.getYearFrom());
      String to = model.getYearTo() == null ? "по сей день" : String.valueOf(model.getYearTo());
      return from + "–" + to;
   }

   private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
   }

   private static String normalizeVin(String vin) {
      String value = blankToNull(vin);
      return value == null ? null : value.toUpperCase(Locale.ROOT);
   }
}
