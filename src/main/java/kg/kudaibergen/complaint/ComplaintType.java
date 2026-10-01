package kg.kudaibergen.complaint;

/** На что жалоба; targetId — id этого объекта. */
public enum ComplaintType {
   /** «Это мой контейнер» — устарело: такие заявки теперь споры за контейнер (container_disputes). */
   CONTAINER_CLAIM,
   /** На магазин целиком. */
   SHOP,
   /** На запчасть (targetId — id запчасти). */
   PART,
   /** На фото места магазина (targetId — id фото, media). */
   SHOP_PHOTO,
   /** На отзыв о магазине. */
   REVIEW,
   /** На отзыв о мастере. */
   MASTER_REVIEW,
   /** На чат целиком (targetId — id чата) — «Пожаловаться» из меню чата. */
   CHAT,
   /** На одно сообщение в чате. */
   CHAT_MESSAGE,
   /** На мастера / СТО. */
   MASTER,
   /** На отклик мастера на заявку. */
   SERVICE_OFFER
}
