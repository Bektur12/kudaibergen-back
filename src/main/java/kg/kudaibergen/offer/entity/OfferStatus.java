package kg.kudaibergen.offer.entity;

/** Статус предложения. В БД — нативный enum offer_status. */
public enum OfferStatus {
   ACTIVE,
   ACCEPTED,
   REJECTED,
   CANCELLED,
   EXPIRED
}
