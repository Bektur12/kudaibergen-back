package kg.kudaibergen.master.dto;

/**
 * Что показать клиенту: «Ждём откликов», «N могут помочь», время вышло без откликов (предложить расширить
 * радиус), время вышло с откликами, «Закрыта».
 */
public enum ServiceRequestState {
   WAITING,
   HAS_OFFERS,
   NO_OFFERS,
   EXPIRED,
   CLOSED
}
