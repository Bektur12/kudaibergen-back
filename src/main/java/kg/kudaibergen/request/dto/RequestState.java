package kg.kudaibergen.request.dto;

/**
 * Что показать покупателю: «Ждём ответов», «N ответили» (зелёный), «Пока никто не ответил» (экран 20 —
 * время вышло без «Есть»), «Время вышло» с ответами (можно продлить или закрыть), «Закрыт».
 */
public enum RequestState {
   WAITING,
   HAS_ANSWERS,
   NO_ANSWERS,
   EXPIRED,
   CLOSED
}
