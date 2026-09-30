package kg.kudaibergen.master.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.auth.dto.PhoneFormat;
import kg.kudaibergen.garage.entity.CarOrigin;

/** Тела запросов профиля мастера (38). */
public final class MasterInputs {

   private MasterInputs() {
   }

   /**
    * «Стать мастером»: что делаю, с какими марками (allBrands — все), какие машины (origins пусто — любые),
    * где (адрес и точка), в каком радиусе, выезжаю ли, часы работы.
    */
   public record CreateMaster(
         @NotBlank(message = "Укажите название") String name,
         @NotEmpty(message = "Выберите хотя бы одну услугу") Set<String> services,
         Boolean allBrands,
         Set<Long> brandIds,
         Set<CarOrigin> origins,
         @NotBlank(message = "Укажите адрес") @Size(max = 160) String address,
         @NotNull(message = "Поставьте точку на карте") @DecimalMin("-90") @DecimalMax("90") Double lat,
         @NotNull(message = "Поставьте точку на карте") @DecimalMin("-180") @DecimalMax("180") Double lng,
         @Min(value = 1, message = "Радиус — от 1 до 30 км") @Max(value = 30, message = "Радиус — от 1 до 30 км") Integer radiusKm,
         Boolean mobile,
         LocalTime openFrom,
         LocalTime openTo,
         Set<DayOfWeek> workDays,
         @Pattern(regexp = "|" + PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE) String phone) {
   }

   /** Частичное изменение: null — не менять. */
   public record UpdateMaster(
         String name,
         Set<String> services,
         Boolean allBrands,
         Set<Long> brandIds,
         Set<CarOrigin> origins,
         @Size(max = 160) String address,
         @DecimalMin("-90") @DecimalMax("90") Double lat,
         @DecimalMin("-180") @DecimalMax("180") Double lng,
         @Min(value = 1, message = "Радиус — от 1 до 30 км") @Max(value = 30, message = "Радиус — от 1 до 30 км") Integer radiusKm,
         Boolean mobile,
         LocalTime openFrom,
         LocalTime openTo,
         Set<DayOfWeek> workDays,
         @Pattern(regexp = "|" + PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE) String phone) {
   }

   /** «● Принимаю» (39): выключено — заявки не приходят. */
   public record SetAccepting(@NotNull Boolean accepting) {
   }

   /** Фото работ и места: до 8, первое — обложка (purpose=MASTER). */
   public record Photos(@NotNull @Size(max = 8, message = "Не больше 8 фото") List<Long> mediaIds) {
   }

   public record Avatar(@NotNull(message = "Загрузите фото") Long mediaId) {
   }
}
