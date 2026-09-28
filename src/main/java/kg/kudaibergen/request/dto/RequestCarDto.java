package kg.kudaibergen.request.dto;

import kg.kudaibergen.garage.dto.BrandDto;

/** Машина запроса (снимок): label «Toyota Camry 50 · 2012». carId = null — машину удалили из гаража. */
public record RequestCarDto(Long carId, BrandDto brand, Long modelId, String modelLabel, int year, String label) {
}
