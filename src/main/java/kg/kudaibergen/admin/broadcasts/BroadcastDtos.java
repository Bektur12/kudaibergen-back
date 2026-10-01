package kg.kudaibergen.admin.broadcasts;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

/** Рассылки [A6]. */
public final class BroadcastDtos {

   private BroadcastDtos() {
   }

   /** Кому: все, покупатели (режим «покупатель»), люди действующих боксов, действующие мастера. */
   public enum Audience { ALL, BUYERS, SELLERS, MASTERS }

   public enum BroadcastStatus { DRAFT, SCHEDULED, SENDING, SENT, CANCELLED }

   /**
    * Фильтры аудитории; пусто — без фильтра. brandIds — продавцы и мастера этих марок, покупатели с машиной этой
    * марки; rowIds — продавцы в этих рядах; serviceTypes — мастера с этими услугами.
    */
   public record BroadcastFilters(@Nullable List<Long> brandIds, @Nullable List<Long> rowIds,
                                  @Nullable List<String> serviceTypes) {

      public static BroadcastFilters none() {
         return new BroadcastFilters(List.of(), List.of(), List.of());
      }
   }

   /** «Получат 64 продавца»: recipients — всего, withDevices — у скольких есть приложение с пушами. */
   public record EstimateDto(long recipients, long withDevices, String label) {
   }

   /** Черновик. Тексты KG необязательны — без них кыргызоязычные получат русский. */
   public record BroadcastInput(
         @NotNull Audience audience,
         @Valid BroadcastFilters filters,
         @NotBlank(message = "Заголовок") @Size(max = 80) String titleRu,
         @Size(max = 80) String titleKg,
         @NotBlank(message = "Текст") @Size(max = 500) String bodyRu,
         @Size(max = 500) String bodyKg) {
   }

   /** Запланировать: at — когда (по UTC), now = true — сразу. В тихие часы 22:00–07:00 переносится на 07:00. */
   public record ScheduleRequest(@Nullable Instant at, @Nullable Boolean now) {
   }

   /**
    * Рассылка: в истории — заголовок, кому, получили (delivered), открыли (opened, openedPct).
    * deferred — время попало в тихие часы и отправка перенесена на 07:00.
    */
   public record BroadcastDto(Long id, Audience audience, BroadcastFilters filters, String titleRu,
                              @Nullable String titleKg, String bodyRu, @Nullable String bodyKg,
                              BroadcastStatus status, @Nullable Instant scheduledAt, @Nullable Instant startedAt,
                              @Nullable Instant sentAt, int recipients, int delivered, int opened,
                              @Nullable Integer openedPct, @Nullable Long createdBy, @Nullable String createdByName,
                              Instant createdAt, boolean deferred) {
   }

   public record BroadcastCounts(long all, long drafts, long scheduled, long sent) {
   }
}
