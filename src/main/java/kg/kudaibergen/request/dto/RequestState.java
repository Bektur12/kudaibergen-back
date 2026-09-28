package kg.kudaibergen.request.dto;

/**
 * Что показать покупателю: «Ждём ответов», «N ответили» (зелёный), «Пока никто не ответил» (экран 20),
 * «Закрыт». EXPIRED — закрыт автоматически через 7 дней без действий.
 */
public enum RequestState {
   WAITING,
   HAS_ANSWERS,
   NO_ANSWERS,
   CLOSED,
   EXPIRED
}
