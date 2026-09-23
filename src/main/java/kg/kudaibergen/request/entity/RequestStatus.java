package kg.kudaibergen.request.entity;

/** Статус запроса. В БД — нативный enum request_status. */
public enum RequestStatus {
   ACTIVE,
   COMPLETED,
   EXPIRED,
   CANCELLED
}
