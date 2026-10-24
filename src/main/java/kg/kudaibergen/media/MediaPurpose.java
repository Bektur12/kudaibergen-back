package kg.kudaibergen.media;

/**
 * Для чего загружено фото: товар (26), фото места бокса (22), аватар, фото к запросу (06) и к ответу «Есть» (12),
 * фото работ мастера (38) и к заявке на услугу (35). Голосовые — REQUEST, REPLY, SERVICE и OFFER.
 */
public enum MediaPurpose {
   PART,
   SHOP,
   AVATAR,
   REQUEST,
   REPLY,
   MASTER,
   SERVICE,
   /** Логотип марки (админка, справочники). */
   BRAND,
   /** Голосовое к отклику мастера (19). */
   OFFER
}
