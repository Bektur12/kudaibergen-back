package kg.kudaibergen.user.dto;

import kg.kudaibergen.user.entity.Vehicle;

public record VehicleResponse(Long id, String brand, String model, String generation, Short year,
                              String engine, String bodyType, String vin, boolean isDefault, String title) {

   public static VehicleResponse of(Vehicle vehicle) {
      return new VehicleResponse(vehicle.getId(), vehicle.getBrand(), vehicle.getModel(),
            vehicle.getGeneration(), vehicle.getYear(), vehicle.getEngine(), vehicle.getBodyType(),
            vehicle.getVin(), vehicle.isDefault(), vehicle.describe());
   }
}
