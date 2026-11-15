package kg.kudaibergen.master.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import kg.kudaibergen.garage.entity.CarOrigin;

/**
 * Справочник «Кто рядом» на телефон: все действующие мастера, СТО и эвакуаторы страны — без фото и ссылок.
 * Хранится на телефоне; без интернета (перевал, трасса) приложение само считает расстояние по GPS и даёт
 * позвонить или написать SMS. version меняется, только когда меняются сами данные.
 */
public record OfflinePackDto(String version, Instant generatedAt, List<PackServiceType> serviceTypes,
                             List<EmergencyNumber> emergency, List<PackMaster> masters) {

   /** Услуга для чипсов без сети. */
   public record PackServiceType(String code, String nameRu, String nameKg, String icon, boolean urgent,
                                 int maxRadiusKm) {
   }

   /** Экстренная служба — показывать всегда, даже когда справочник пустой. */
   public record EmergencyNumber(String number, String nameRu, String nameKg) {
   }

   /**
    * Мастер кратко. radiusKm — сам сказал, что доедет так далеко; allBrands / brandIds / origins — с какими
    * машинами работает (пусто origins — любые).
    */
   public record PackMaster(long id, String name, String phone, double lat, double lng, String address,
                            List<String> services, boolean mobile, int radiusKm, BigDecimal rating,
                            int reviewsCount, boolean allBrands, List<Long> brandIds, List<CarOrigin> origins) {
   }

   /** «Есть ли обновление»: сверить с сохранённой version, не скачивая весь справочник. */
   public record Version(String version, int mastersCount) {
   }
}
