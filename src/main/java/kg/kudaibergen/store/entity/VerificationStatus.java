package kg.kudaibergen.store.entity;

/** Статус проверки магазина. В БД — нативный enum verification_status. */
public enum VerificationStatus {
   NEW,
   VERIFIED,
   TRUSTED,
   BLOCKED
}
