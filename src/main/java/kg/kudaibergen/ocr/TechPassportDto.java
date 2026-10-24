package kg.kudaibergen.ocr;

import java.math.BigDecimal;

import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.dto.ModelDto;
import kg.kudaibergen.garage.entity.FuelType;
import org.springframework.lang.Nullable;

/**
 * Что прочитали в техпаспорте (04 «Фото техпаспорта»): марка и модель из справочника, год, VIN, объём и топливо.
 * Не нашли — null: model = null — показать выбор модели марки, brand = null — выбор марки. Дальше —
 * POST /me/cars {modelId, year, vin, engineVolume, fuel, fromTechPassport: true}.
 */
public record TechPassportDto(@Nullable BrandDto brand, @Nullable ModelDto model, @Nullable Integer year,
                              @Nullable String vin, @Nullable BigDecimal engineVolume, @Nullable FuelType fuel) {
}
