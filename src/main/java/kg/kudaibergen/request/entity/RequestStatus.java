package kg.kudaibergen.request.entity;

/**
 * ACTIVE — продавцы видят и отвечают до expiresAt; EXPIRED — время вышло, покупатель может продлить
 * или расширить адресатов; CLOSED — покупатель закрыл («Купил» или вручную).
 */
public enum RequestStatus {
   ACTIVE,
   EXPIRED,
   CLOSED
}
