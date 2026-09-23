package kg.kudaibergen.user;

import java.util.List;

import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.user.dto.CreateVehicleRequest;
import kg.kudaibergen.user.dto.UpdateVehicleRequest;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.Vehicle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

   private final VehicleRepository vehicles;
   private final UserService userService;

   public VehicleService(VehicleRepository vehicles, UserService userService) {
      this.vehicles = vehicles;
      this.userService = userService;
   }

   @Transactional(readOnly = true)
   public List<Vehicle> list(Long userId) {
      return vehicles.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
   }

   /** Машина из профиля: её описание подставляется в запрос вместо уточняющих вопросов продавца. */
   @Transactional(readOnly = true)
   public Vehicle getOwned(Long vehicleId, Long userId) {
      return vehicles.findByIdAndUserId(vehicleId, userId)
            .orElseThrow(() -> new NotFoundException("VEHICLE_NOT_FOUND", "Автомобиль не найден"));
   }

   @Transactional
   public Vehicle create(Long userId, CreateVehicleRequest request) {
      User user = userService.getRequired(userId);
      Vehicle vehicle = new Vehicle(user);
      vehicle.setBrand(request.brand().trim());
      vehicle.setModel(request.model().trim());
      vehicle.setGeneration(request.generation());
      vehicle.setYear(request.year());
      vehicle.setEngine(request.engine());
      vehicle.setBodyType(request.bodyType());
      vehicle.setVin(request.vin());

      boolean first = vehicles.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).isEmpty();
      boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || first;
      if (makeDefault) {
         vehicles.clearDefault(userId);
      }
      vehicle.setDefault(makeDefault);
      return vehicles.save(vehicle);
   }

   @Transactional
   public Vehicle update(Long vehicleId, Long userId, UpdateVehicleRequest request) {
      Vehicle vehicle = getOwned(vehicleId, userId);
      if (request.brand() != null) {
         vehicle.setBrand(request.brand().trim());
      }
      if (request.model() != null) {
         vehicle.setModel(request.model().trim());
      }
      if (request.generation() != null) {
         vehicle.setGeneration(request.generation());
      }
      if (request.year() != null) {
         vehicle.setYear(request.year());
      }
      if (request.engine() != null) {
         vehicle.setEngine(request.engine());
      }
      if (request.bodyType() != null) {
         vehicle.setBodyType(request.bodyType());
      }
      if (request.vin() != null) {
         vehicle.setVin(request.vin());
      }
      if (Boolean.TRUE.equals(request.isDefault()) && !vehicle.isDefault()) {
         vehicles.clearDefault(userId);
         vehicle.setDefault(true);
      } else if (Boolean.FALSE.equals(request.isDefault())) {
         vehicle.setDefault(false);
      }
      return vehicle;
   }

   @Transactional
   public void delete(Long vehicleId, Long userId) {
      vehicles.delete(getOwned(vehicleId, userId));
   }
}
