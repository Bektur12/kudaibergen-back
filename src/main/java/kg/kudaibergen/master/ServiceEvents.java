package kg.kudaibergen.master;

import java.util.Collection;

/** События заявок на услуги. Публикуются в транзакции, обрабатываются после коммита. */
public final class ServiceEvents {

   private ServiceEvents() {
   }

   /** Заявка ушла этим мастерам: создание или расширение радиуса. */
   public record ServiceDispatched(Long requestId, Collection<Long> masterIds) {
   }

   /** Мастер ответил «Могу помочь». */
   public record ServiceOfferReceived(Long requestId, Long offerId) {
   }

   /** Время вышло; canHelpCount — сколько мастеров откликнулись. */
   public record ServiceExpired(Long requestId, int canHelpCount) {
   }

   /** Клиент закрыл заявку; masterId = null — ни с кем не договорился. */
   public record ServiceClosed(Long requestId, Long masterId, Integer stars) {
   }

   /** Изменилась статистика заявки (37) — живое обновление клиенту. */
   public record ServiceStatsChanged(Long requestId) {
   }
}
