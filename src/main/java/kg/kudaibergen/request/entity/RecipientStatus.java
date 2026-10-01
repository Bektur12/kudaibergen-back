package kg.kudaibergen.request.entity;

/**
 * Что с запросом у получателя (статистика 32): доставлен, открыт, ответил «Есть» / «Нет»,
 * время запроса вышло без ответа. После продления EXPIRED возвращается в DELIVERED или SEEN.
 */
public enum RecipientStatus {
   DELIVERED,
   SEEN,
   HAVE,
   NOT_HAVE,
   EXPIRED
}
