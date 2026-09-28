package kg.kudaibergen.chat.entity;

import kg.kudaibergen.user.entity.Lang;

/**
 * Быстрые ответы (ТЗ 11.1). MESSAGE — уходит сообщением в чат, ACTION — клиент выполняет действие
 * сам (открывает маршрут или шторку закрытия запроса), сообщение не создаётся.
 */
public enum QuickReply {

   // ── покупатель ──
   /** «Как пройти к боксу» → экран маршрута 18. */
   ROUTE_TO_BOX(ChatSide.BUYER, Kind.ACTION, "Как пройти к боксу", "Бокска кантип барам"),
   /** «Купил — закрыть запрос» → шторка 09 (POST /requests/{id}/close). */
   CLOSE_REQUEST(ChatSide.BUYER, Kind.ACTION, "Купил — закрыть запрос", "Сатып алдым — суроону жабуу"),
   /** «Я на месте» с экрана маршрута 18: продавцу плашка «Покупатель подошёл». */
   ARRIVED(ChatSide.BUYER, Kind.MESSAGE, "Покупатель подошёл", "Сатып алуучу келди"),

   // ── продавец ──
   RESERVED(ChatSide.SHOP, Kind.MESSAGE, "Отложил для вас", "Сиз үчүн калтырдым"),
   /** «Как пройти» — карточка маршрута до бокса с кнопкой «Маршрут». */
   ROUTE(ChatSide.SHOP, Kind.MESSAGE, "Как пройти к боксу", "Бокска кантип барса болот"),
   /** «Продано» — покупателю кнопка «Закрыть запрос». */
   SOLD(ChatSide.SHOP, Kind.MESSAGE, "Продано", "Сатылды");

   public enum Kind {
      MESSAGE,
      ACTION
   }

   private final ChatSide side;
   private final Kind kind;
   private final String ru;
   private final String kg;

   QuickReply(ChatSide side, Kind kind, String ru, String kg) {
      this.side = side;
      this.kind = kind;
      this.ru = ru;
      this.kg = kg;
   }

   public ChatSide side() {
      return side;
   }

   public Kind kind() {
      return kind;
   }

   public String label(Lang lang) {
      return lang == Lang.KG ? kg : ru;
   }
}
