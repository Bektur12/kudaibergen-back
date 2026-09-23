package kg.kudaibergen.notification.entity;

/** Тип уведомления. Лежит внутри payload — по нему работает батчинг. */
public enum NotificationType {
   NEW_REQUEST,
   NEW_MESSAGE
}
