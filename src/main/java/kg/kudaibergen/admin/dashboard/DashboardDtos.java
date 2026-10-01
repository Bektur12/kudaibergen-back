package kg.kudaibergen.admin.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import kg.kudaibergen.admin.access.MaskedPhone;
import org.springframework.lang.Nullable;

/** Сводка [A1]. generatedAt — когда посчитано («данные обновлены 2 мин назад»): агрегаты кэшируются на 90 с. */
public final class DashboardDtos {

   private DashboardDtos() {
   }

   public enum ChartRange { D14, D30, QUARTER }

   public enum DashboardPeriod { WEEK, MONTH }

   /**
    * KPI: requestsToday и requestsDeltaPct — запросов сегодня и % к среднему за прошлые 7 дней;
    * haveIn30Pct и haveIn30DeltaPp — доля запросов за 7 дней, получивших «Есть» за 30 минут, и изменение
    * в процентных пунктах к прошлой неделе; activeShops и activeShopsNewWeek — действующих боксов и сколько
    * из них стали действующими за неделю; serviceRequestsToday и serviceDeltaPct — заявок сегодня и % к вчера.
    * Проценты null — не с чем сравнить.
    */
   public record KpiDto(long requestsToday, @Nullable Integer requestsDeltaPct, @Nullable Integer haveIn30Pct,
                        @Nullable Integer haveIn30DeltaPp, long activeShops, long activeShopsNewWeek,
                        long serviceRequestsToday, @Nullable Integer serviceDeltaPct, Instant generatedAt) {
   }

   /** Точка графика: день (Бишкек), запросов, из них с «Есть», заявок на услуги. */
   public record DayPoint(LocalDate date, long requests, long withHave, long serviceRequests) {
   }

   public record RequestsByDayDto(ChartRange range, List<DayPoint> days, Instant generatedAt) {
   }

   /** Марка: запросов за период, без «Есть» за 30 минут и их доля; sellers — действующих боксов с этой маркой. */
   public record BrandUnanswered(Long brandId, String brand, long requests, long unanswered, int pct, long sellers) {
   }

   /**
    * Подсказка по худшей марке: text — готовый текст для карточки («BMW: 41% запросов без ответа…»),
    * broadcastBrandIds — предзаполненный фильтр рассылки «продают BMW» (аудитория SELLERS).
    */
   public record BrandHint(Long brandId, String text, List<Long> broadcastBrandIds) {
   }

   public record UnansweredByBrandDto(DashboardPeriod period, List<BrandUnanswered> brands, @Nullable BrandHint hint,
                                      Instant generatedAt) {
   }

   /** «Чаще всего ищут»: подсказка, иначе текст запроса как есть; havePct — доля получивших «Есть». */
   public record SearchedItem(String label, long requests, int havePct) {
   }

   public record TopSearchedDto(DashboardPeriod period, List<SearchedItem> items, Instant generatedAt) {
   }

   /** Пункт «Ждут действий»: kind — SHOP / MASTER / COMPLAINT / DISPUTE, id, подпись, когда подано. */
   public record PendingItem(String kind, Long id, String title, @Nullable @MaskedPhone String phone, Instant createdAt) {
   }

   public record PendingDto(long shops, long masters, long complaints, long disputes, List<PendingItem> latest,
                            Instant generatedAt) {
   }
}
