package kg.kudaibergen.master.dto;

import java.math.BigDecimal;

import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.garage.entity.FuelType;
import org.springframework.lang.Nullable;

/** Машина заявки: label «Toyota Camry 50 · 2012 · 2.5 бензин», страна — для мастеров «японец / европеец». */
public record ServiceCarDto(@Nullable Long carId, BrandDto brand, @Nullable Long modelId, @Nullable String modelLabel,
                            @Nullable Integer year, @Nullable BigDecimal engineVolume, @Nullable FuelType fuel,
                            CarOrigin origin, String label) {
}
