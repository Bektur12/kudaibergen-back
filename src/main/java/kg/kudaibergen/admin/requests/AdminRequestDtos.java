package kg.kudaibergen.admin.requests;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.admin.access.MaskedPhone;
import kg.kudaibergen.media.MediaItemDto;
import org.springframework.lang.Nullable;

/** Запросы на запчасти и заявки на услуги: мониторинг [A8]. */
public final class AdminRequestDtos {

   private AdminRequestDtos() {
   }

   /** Период: сегодня (по Бишкеку), 7 дней, 30 дней, всё время. */
   public enum MonitorPeriod { TODAY, WEEK, MONTH, ALL }

   /**
    * Статус для таблицы: ACTIVE — «Активна», AGREED — «Договорились» (закрыт с продавцом / мастером),
    * NO_REPLIES — «Без откликов» (время вышло, «Есть» / «Могу помочь» ноль), EXPIRED — «Время вышло»,
    * CLOSED — закрыт без сделки, HIDDEN — скрыт администрацией.
    */
   public enum MonitorStatus { ACTIVE, AGREED, NO_REPLIES, EXPIRED, CLOSED, HIDDEN }

   public record MonitorBuyerDto(Long userId, @Nullable String name, @MaskedPhone String phone) {
   }

   /**
    * Строка: received — получили, seen — посмотрели, positive — «Есть» / «Могут» («получили / посмотрели / могут»).
    * service — код услуги (только у заявок).
    */
   public record MonitorRow(Long id, String text, MonitorBuyerDto buyer, Instant createdAt, String carLabel, int received,
                            long seen, int positive, MonitorStatus status, @Nullable String service, boolean urgent,
                            Instant expiresAt) {
   }

   /** Табы и чипы: «Запросы на запчасти · 342», «Заявки на услуги · 57», «Без откликов · 6» — за выбранный период. */
   public record MonitorCounts(long parts, long services, long partsNoReplies, long servicesNoReplies) {
   }

   /** Ответ продавца на запрос: answeredAfterMin — через сколько минут после рассылки. */
   public record AdminReplyDto(Long id, Long shopId, String shopName, String answer, @Nullable String condition,
                               @Nullable Integer price, @Nullable String message, Instant createdAt,
                               @Nullable Integer answeredAfterMin) {
   }

   public record RecipientCountsDto(long delivered, long seen, long positive, long negative, long silent) {
   }

   public record AdminPartRequestDto(Long id, String text, MonitorBuyerDto buyer, String carLabel, String target,
                                     MonitorStatus status, String duration, Instant createdAt, Instant expiresAt,
                                     @Nullable Instant closedAt, @Nullable Long closedWithShopId,
                                     @Nullable String hiddenReason, RecipientCountsDto counts,
                                     List<AdminReplyDto> replies, List<MediaItemDto> photos) {
   }

   /** Отклик мастера: answeredAfterMin — через сколько минут после того, как заявка пришла мастеру. */
   public record AdminOfferDto(Long id, Long masterId, String masterName, String answer, @Nullable Integer priceFrom,
                               @Nullable Instant availableAt, @Nullable String message, Instant createdAt,
                               @Nullable Integer answeredAfterMin, boolean hidden) {
   }

   /** Машина снимком: объём, топливо, страна. */
   public record AdminServiceCarDto(String label, @Nullable BigDecimal engineVolume, @Nullable String fuel,
                                    String origin) {
   }

   public record AdminServiceRequestDto(Long id, String service, boolean urgent, AdminServiceCarDto car,
                                        String description, List<MediaItemDto> media, MonitorBuyerDto buyer, String when,
                                        @Nullable Instant atTime, String where, double lat, double lng,
                                        @Nullable String address, int radiusKm, MonitorStatus status, String duration,
                                        Instant createdAt, Instant expiresAt, int extendedTimes,
                                        @Nullable Instant closedAt, @Nullable Long closedWithMasterId,
                                        @Nullable String hiddenReason, RecipientCountsDto counts,
                                        List<AdminOfferDto> offers) {
   }

   public record HideRequestBody(@NotBlank(message = "Укажите причину") @Size(max = 300) String reason) {
   }

   /** Канал Centrifugo админки: подключение и подписка на admin:requests. */
   public record AdminRealtimeDto(String connectionToken, String subscriptionToken, String channel,
                                  long expiresInSeconds) {
   }
}
