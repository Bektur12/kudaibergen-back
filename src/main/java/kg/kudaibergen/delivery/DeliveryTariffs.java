package kg.kudaibergen.delivery;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kg.kudaibergen.common.config.ClockConfig;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.delivery.dto.DeliveryTariffsDto;
import kg.kudaibergen.delivery.entity.DeliveryDistrict;
import kg.kudaibergen.delivery.entity.DeliveryRegion;
import org.springframework.stereotype.Component;

/**
 * Тарифы Ылдам Экспресс до 5 кг: Бишкек — сегодня, области — 1 день, Баткенская обл. и отдалённые районы
 * (Чон-Алай, Аксы, Ала-Бука, Алай) — 2 дня. Доплату за вес и размер считает курьер. Отсечка 17:30 по Бишкеку:
 * упаковали раньше — курьер заберёт сегодня, позже — завтра.
 */
@Component
public class DeliveryTariffs {

   static final String SURCHARGE_NOTE = "Больше 5 кг и 35 × 35 × 35 см — +50 с за каждые 5 кг, посчитает курьер";
   private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

   /** Тариф: price null — цену назовёт курьер; days 0 — сегодня. */
   public record Tariff(Integer price, int days) {
   }

   private final DeliveryProperties properties;
   private final Clock clock;

   public DeliveryTariffs(DeliveryProperties properties, Clock clock) {
      this.properties = properties;
      this.clock = clock;
   }

   public Tariff of(DeliveryRegion region, DeliveryDistrict district) {
      if (district != null) {
         if (district.region() != region) {
            throw new BadRequestException("BAD_DISTRICT", "Район не относится к выбранной области");
         }
         return new Tariff(properties.remotePrice(), 2);
      }
      if (region == DeliveryRegion.BISHKEK) {
         return new Tariff(properties.bishkekPrice(), 0);
      }
      return region.remote() ? new Tariff(properties.remotePrice(), 2) : new Tariff(properties.standardPrice(), 1);
   }

   public DeliveryTariffsDto dto() {
      List<DeliveryTariffsDto.RegionDto> regions = new ArrayList<>();
      for (DeliveryRegion region : DeliveryRegion.values()) {
         Tariff tariff = of(region, null);
         List<DeliveryTariffsDto.DistrictDto> districts = Arrays.stream(DeliveryDistrict.values())
               .filter(district -> district.region() == region)
               .map(district -> new DeliveryTariffsDto.DistrictDto(district, district.title(), 2,
                     properties.remotePrice()))
               .toList();
         regions.add(new DeliveryTariffsDto.RegionDto(region, region.title(), region.inName(), tariff.days(),
               tariff.price(), districts));
      }
      String remoteNote = "Чон-Алай, Аксы (Кербен), Ала-Бука и Алай (Гульчо) — отдалённые: 2 дня, "
            + properties.remotePrice() + " с";
      return new DeliveryTariffsDto(regions, cutoff().format(HH_MM), remoteNote, SURCHARGE_NOTE,
            properties.courierName(), properties.courierPhone());
   }

   /**
    * Курьер заберёт сегодня: упаковали (at) сегодня до отсечки по Бишкеку — или ещё вчера и раньше (тогда
    * курьер приезжает в ближайший день, то есть сегодня).
    */
   public boolean pickupToday(Instant at) {
      ZonedDateTime packed = at.atZone(ClockConfig.MARKET_ZONE);
      LocalDate today = ZonedDateTime.now(clock.withZone(ClockConfig.MARKET_ZONE)).toLocalDate();
      if (packed.toLocalDate().isBefore(today)) {
         return true;
      }
      return packed.toLocalDate().equals(today) && packed.toLocalTime().isBefore(cutoff());
   }

   /** Сегодняшняя отсечка — «ещё 6 ч 25 мин» в админке. */
   public Instant cutoffAt() {
      ZonedDateTime now = ZonedDateTime.now(clock.withZone(ClockConfig.MARKET_ZONE));
      return now.toLocalDate().atTime(cutoff()).atZone(ClockConfig.MARKET_ZONE).toInstant();
   }

   public Instant now() {
      return clock.instant();
   }

   private LocalTime cutoff() {
      return properties.cutoff() == null ? LocalTime.of(17, 30) : properties.cutoff();
   }

   public DeliveryProperties properties() {
      return properties;
   }
}
