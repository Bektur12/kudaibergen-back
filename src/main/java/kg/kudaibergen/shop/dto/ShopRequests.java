package kg.kudaibergen.shop.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.auth.dto.PhoneFormat;

/** Запросы кабинета продавца. */
public final class ShopRequests {

   private ShopRequests() {
   }

   /**
    * Регистрация бокса одним запросом по «Сохранить» на экране 10: контейнер из 10а + профиль.
    * Часы по умолчанию 08:00–17:00 без выходных.
    */
   public record CreateShop(
         @NotNull(message = "Выберите контейнер") Long containerId,
         @NotBlank(message = "Укажите название") String name,
         @NotEmpty(message = "Выберите хотя бы одну марку") Set<Long> brandIds,
         @NotEmpty(message = "Выберите хотя бы одну категорию") Set<Long> categoryIds,
         LocalTime openFrom,
         LocalTime openTo,
         Set<DayOfWeek> workDays) {
   }

   /** Частичное обновление профиля (только владелец). phone: пустая строка — убрать. */
   public record UpdateShop(
         String name,
         LocalTime openFrom,
         LocalTime openTo,
         @Size(min = 1, message = "Нужен хотя бы один рабочий день") Set<DayOfWeek> workDays,
         @Pattern(regexp = "|" + PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE) String phone,
         Boolean phoneVisible) {
   }

   public record SetBrands(@NotEmpty(message = "Выберите хотя бы одну марку") Set<Long> brandIds) {
   }

   public record SetCategories(@NotEmpty(message = "Выберите хотя бы одну категорию") Set<Long> categoryIds) {
   }

   /** Тумблер «Бокс закрыт» (экран 21). */
   public record SetOpen(@NotNull Boolean isOpen) {
   }

   public record AddMember(
         @NotBlank @Pattern(regexp = PhoneFormat.E164_KG, message = PhoneFormat.MESSAGE) String phone) {
   }

   /** Переезд: «Мой бокс» → «Изменить» → новый контейнер. */
   public record Relocate(@NotNull(message = "Выберите контейнер") Long containerId) {
   }

   /** Скан наклейки на контейнере + GPS телефона (не сохраняется). */
   public record QrVerification(@NotBlank String qrToken, Double lat, Double lon) {
   }

   public record SmsConfirm(
         @NotBlank @Pattern(regexp = "\\d{4}", message = "Код состоит из 4 цифр") String code) {
   }

   /** Ссылка на загруженное фото (POST /media/photos). */
   public record MediaRef(@NotNull(message = "Загрузите фото") Long mediaId) {
   }

   /** Новый порядок фото места: все id. */
   public record PhotoOrder(@NotEmpty(message = "Передайте фото") List<Long> mediaIds) {
   }
}
