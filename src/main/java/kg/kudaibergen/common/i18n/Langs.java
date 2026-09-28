package kg.kudaibergen.common.i18n;

import java.util.Locale;

import kg.kudaibergen.user.entity.Lang;

/** Язык ответа по заголовку Accept-Language: ky/kg — кыргызский, всё остальное — русский. */
public final class Langs {

   private Langs() {
   }

   public static Lang fromHeader(String acceptLanguage) {
      if (acceptLanguage == null || acceptLanguage.isBlank()) {
         return Lang.RU;
      }
      String first = acceptLanguage.split(",")[0].trim().toLowerCase(Locale.ROOT);
      return first.startsWith("ky") || first.startsWith("kg") ? Lang.KG : Lang.RU;
   }
}
