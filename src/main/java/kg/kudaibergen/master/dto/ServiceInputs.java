package kg.kudaibergen.master.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.garage.entity.FuelType;
import kg.kudaibergen.master.entity.OfferAnswer;
import kg.kudaibergen.master.entity.ServiceDuration;
import kg.kudaibergen.master.entity.ServiceWhen;
import kg.kudaibergen.master.entity.ServiceWhere;
import kg.kudaibergen.request.entity.ReviewTag;

/** Тела запросов заявок на услуги (33–37, 39). */
public final class ServiceInputs {

   public static final int MAX_PHOTOS = 5;

   private ServiceInputs() {
   }

   /**
    * «Отправить» (36). Машина — carId из гаража или brandId + modelId? + year? (шаги 34–35), объём, топливо
    * и страна — по желанию (страна по умолчанию — по марке). lat/lng — где клиент или куда ехать эвакуатору.
    * duration по умолчанию — из справочника услуги (эвакуатор — 15 минут).
    */
   public record CreateServiceRequest(
         @NotBlank(message = "Выберите услугу") String service,
         Long carId,
         Long brandId,
         Long modelId,
         @Min(value = 1950, message = "Год не раньше 1950") @Max(value = 2100, message = "Некорректный год") Integer year,
         @DecimalMin(value = "0.5", message = "Объём — от 0.5 до 9.9 л") @DecimalMax(value = "9.9", message = "Объём — от 0.5 до 9.9 л")
         BigDecimal engineVolume,
         FuelType fuel,
         CarOrigin origin,
         @NotBlank(message = "Опишите, что случилось")
         @Size(min = 10, max = 500, message = "От 10 до 500 символов") String description,
         @Size(max = MAX_PHOTOS, message = "Не больше 5 фото") List<Long> mediaIds,
         @NotNull(message = "Выберите, когда") ServiceWhen when,
         Instant atTime,
         @NotNull(message = "Выберите, где") ServiceWhere where,
         @NotNull(message = "Нужна точка на карте") @DecimalMin("-90") @DecimalMax("90") Double lat,
         @NotNull(message = "Нужна точка на карте") @DecimalMin("-180") @DecimalMax("180") Double lng,
         @Size(max = 160) String address,
         @Min(value = 1, message = "Радиус — от 1 до 50 км") @Max(value = 50, message = "Радиус — от 1 до 50 км") Integer radiusKm,
         ServiceDuration duration) {
   }

   /** «Продлить»: на 30, 60 или 180 минут, до 3 раз. */
   public record ServiceExtend(@NotNull(message = "Выберите, на сколько продлить") Integer minutes) {
   }

   /** «Договорились» (37): с каким мастером (только из откликнувшихся «Могу помочь») и оценка ему. */
   public record Close(
         Long masterId,
         @Min(value = 1, message = "От 1 до 5 звёзд") @Max(value = 5, message = "От 1 до 5 звёзд") Integer stars,
         List<ReviewTag> tags) {
   }

   /** Отклик мастера (39): «Могу помочь» — цена «от», когда могу, сообщение; «Не моё» — без деталей. */
   public record Offer(
         @NotNull(message = "Ответьте «Могу помочь» или «Не моё»") OfferAnswer answer,
         @Positive(message = "Цена — целое число сом больше нуля") Integer priceFrom,
         Instant availableAt,
         @Size(max = 300, message = "Не длиннее 300 символов") String message) {
   }
}
