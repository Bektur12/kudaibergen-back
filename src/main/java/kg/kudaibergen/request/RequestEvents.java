package kg.kudaibergen.request;

import java.util.Collection;

/**
 * События модуля запросов. Публикуются в транзакции, обрабатываются после коммита.
 * Когда появится outbox → Kafka (BACKEND_DESIGN, раздел 0), они станут топиками request.*.
 */
public final class RequestEvents {

   private RequestEvents() {
   }

   /** Запрос ушёл этим боксам: создание или «Отправить всему рынку». */
   public record Dispatched(Long requestId, Collection<Long> shopIds) {
   }

   /** Бокс ответил «Есть». */
   public record HaveReceived(Long requestId, Long replyId) {
   }

   /** Время запроса вышло; haveCount — сколько боксов ответили «Есть». */
   public record Expired(Long requestId, int haveCount) {
   }

   /** Покупатель закрыл запрос; shopId = null — ни с кем. */
   public record Closed(Long requestId, Long shopId, Integer stars) {
   }

   /** Изменились счётчики или списки статистики запроса (32) — отправить покупателю живое обновление. */
   public record StatsChanged(Long requestId) {
   }
}
