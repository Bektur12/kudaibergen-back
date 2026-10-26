package kg.kudaibergen.delivery.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.delivery.entity.DeliveryDistrict;
import kg.kudaibergen.delivery.entity.DeliveryRegion;

public final class DeliveryInputs {

   private DeliveryInputs() {
   }

   /** «Заказать доставку»: куда, адрес, получатель. district — чип отдалённого района (только для его области). */
   public record CreateOrder(
         @NotNull(message = "Выберите товар") Long partId,
         @NotNull(message = "Выберите, куда везти") DeliveryRegion region,
         DeliveryDistrict district,
         @NotBlank(message = "Укажите адрес") @Size(max = 200, message = "Адрес — до 200 символов") String address,
         @NotBlank(message = "Укажите получателя") @Size(max = 60, message = "Имя — до 60 символов")
         String recipientName,
         @NotBlank(message = "Укажите телефон") @Size(max = 20, message = "Некорректный телефон")
         String recipientPhone) {
   }

   /** «Упаковал — жду курьера»: вес для заявки Ылдам и «Хрупкое». */
   public record Packed(
         @NotNull(message = "Укажите вес")
         @DecimalMin(value = "0.1", message = "Вес — от 0,1 кг") @DecimalMax(value = "100", message = "Вес — до 100 кг")
         BigDecimal weightKg,
         Boolean fragile) {
   }
}
