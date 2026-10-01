package kg.kudaibergen.master.entity;

/** Что с заявкой у мастера (статистика 37): доставлена, открыта, «Могу помочь», «Не моё», время вышло без ответа. */
public enum ServiceRecipientStatus {
   DELIVERED,
   SEEN,
   CAN_HELP,
   NOT_MINE,
   EXPIRED
}
