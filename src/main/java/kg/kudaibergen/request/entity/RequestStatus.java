package kg.kudaibergen.request.entity;

/** OPEN — ждёт ответов; CLOSED — покупатель закрыл («Купил» или вручную); EXPIRED — 7 дней без действий. */
public enum RequestStatus {
   OPEN,
   CLOSED,
   EXPIRED
}
