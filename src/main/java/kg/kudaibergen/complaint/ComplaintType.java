package kg.kudaibergen.complaint;

public enum ComplaintType {
   /** «Это мой контейнер» — место занято другим магазином в приложении (ТЗ 7.3). */
   CONTAINER_CLAIM,
   SHOP,
   PART,
   PHOTO,
   REVIEW,
   /** «Пожаловаться» из меню чата. */
   CHAT,
   /** На мастера / СТО. */
   MASTER
}
