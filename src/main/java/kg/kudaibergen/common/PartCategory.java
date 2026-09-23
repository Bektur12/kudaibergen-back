package kg.kudaibergen.common;

/** Справочник категорий запчастей. Живёт в коде: их семь и меняются они вместе с UI. */
public enum PartCategory {
   BRAKES("тормозная система"),
   SUSPENSION("подвеска"),
   ENGINE("двигатель"),
   WHEELS("колёса и диски"),
   LIGHTS("оптика"),
   OILS("масла и жидкости"),
   ACCESSORIES("аксессуары");

   private final String title;

   PartCategory(String title) {
      this.title = title;
   }

   /** Человекочитаемое название — используется в текстах пушей. */
   public String title() {
      return title;
   }
}
