package kg.kudaibergen.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateVehicleRequest(
      @Size(max = 60) String brand,
      @Size(max = 60) String model,
      @Size(max = 60) String generation,
      @Min(1950) @Max(2100) Short year,
      @Size(max = 40) String engine,
      @Size(max = 40) String bodyType,
      @Size(max = 17) String vin,
      Boolean isDefault) {
}
