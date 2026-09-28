package kg.kudaibergen.request.entity;

import kg.kudaibergen.user.entity.Lang;

/** Теги оценки в шторке «Запрос закрыт» (экран 09). */
public enum ReviewTag {
   FAST_REPLY("Быстро ответил", "Тез жооп берди"),
   PART_OK("Деталь как надо", "Тетик туура келди"),
   EASY_TO_FIND("Легко найти бокс", "Боксту табуу оңой");

   private final String ru;
   private final String kg;

   ReviewTag(String ru, String kg) {
      this.ru = ru;
      this.kg = kg;
   }

   public String label(Lang lang) {
      return lang == Lang.KG ? kg : ru;
   }
}
