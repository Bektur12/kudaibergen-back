package kg.kudaibergen.shop.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.auth.dto.PhoneFormat;
import kg.kudaibergen.shop.stock.dto.StockDtos;

/** Запросы кабинета продавца. */
public final class ShopRequests {

   private ShopRequests() {
   }

   /**
    * «Начать получать запросы» (11): на рынке — ряд и номер контейнера (containerId); вне рынка (свой магазин,
    * центр) — containerId не передают, нужны address, lat, lng. Плюс марки. Название по умолчанию — «Ряд 14,
    * конт. 12» (вне рынка — обязательно), категории — по желанию, телефон — номер владельца (для «Позвонить»).
    * Контейнера нет в списке — manualPlace («Ряд К, конт. 5», 2–60 символов) без containerId, address, lat, lng:
    * магазин на рынке без контейнера, название по умолчанию — manualPlace. marketId — на каком рынке это место
    * (GET /market/markets): Дордой, Дордой Моторс, Геология — без схемы, только так; не передан — Кудайберген.
    * stock — «Что у вас всегда есть» (по желанию, можно пропустить): как в PUT /my/shop/stock.
    */
   public record CreateShop(
         Long containerId,
         @Size(max = 160, message = "Адрес — до 160 символов") String address,
         @DecimalMin(value = "-90", message = "Некорректная точка") @DecimalMax(value = "90", message = "Некорректная точка") Double lat,
         @DecimalMin(value = "-180", message = "Некорректная точка") @DecimalMax(value = "180", message = "Некорректная точка") Double lng,
         String name,
         @NotEmpty(message = "Выберите хотя бы одну марку") Set<Long> brandIds,
         Set<Long> categoryIds,
         LocalTime openFrom,
         LocalTime openTo,
         Set<DayOfWeek> workDays,
         @Size(min = 2, max = 60, message = "Место — от 2 до 60 символов") String manualPlace,
         Long marketId,
         @Size(max = StockDtos.MAX_ITEMS, message = "Не больше " + StockDtos.MAX_ITEMS + " позиций")
         List<StockDtos.@Valid StockItemInput> stock) {
   }

   /**
    * Частичное обновление профиля (только владелец). phone: пустая строка — убрать. address, lat, lng — только
    * магазину вне рынка (все три вместе).
    */
   public record UpdateShop(
         String name,
         @Size(min = 1, max = 160, message = "Адрес — от 1 до 160 символов") String address,
         @DecimalMin(value = "-90", message = "Некорректная точка") @DecimalMax(value = "90", message = "Некорректная точка") Double lat,
         @DecimalMin(value = "-180", message = "Некорректная точка") @DecimalMax(value = "180", message = "Некорректная точка") Double lng,
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
