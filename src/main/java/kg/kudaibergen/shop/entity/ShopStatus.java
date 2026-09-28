package kg.kudaibergen.shop.entity;

/**
 * На проверке — продавец заполняет профиль и каталог, но покупатели его не видят и запросы не приходят.
 * Действует — виден на карте, в поиске и в рассылке. Заблокирован — скрыт, продавец видит причину.
 */
public enum ShopStatus {
   PENDING_VERIFICATION,
   ACTIVE,
   BLOCKED
}
