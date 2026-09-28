package kg.kudaibergen.user.entity;

import java.util.Locale;

/** Язык интерфейса: русский или кыргызский (переключатель RU / KG). */
public enum Lang {
   RU(Locale.forLanguageTag("ru")),
   KG(Locale.forLanguageTag("ky"));

   private final Locale locale;

   Lang(Locale locale) {
      this.locale = locale;
   }

   public Locale locale() {
      return locale;
   }
}
