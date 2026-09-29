package kg.kudaibergen.request.dto;

import kg.kudaibergen.garage.dto.BrandDto;
import org.springframework.lang.Nullable;

/** Машина запроса (снимок): label «Toyota Camry 50 · 2012». carId = null — машину удалили из гаража. */
public record RequestCarDto(@Nullable Long carId, BrandDto brand, Long modelId, String modelLabel, int year, String label) {
}
