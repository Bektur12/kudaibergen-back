package kg.kudaibergen.garage;

import java.util.Map;

import kg.kudaibergen.garage.entity.CarOrigin;

/**
 * Страна машины по марке — подсказка по умолчанию (35): Toyota — японец, Hyundai — кореец. Покупатель
 * может поменять: Camry из США — «американец».
 */
public final class CarOrigins {

   private static final Map<String, CarOrigin> BY_BRAND = Map.ofEntries(
         Map.entry("toyota", CarOrigin.JAPAN), Map.entry("lexus", CarOrigin.JAPAN), Map.entry("honda", CarOrigin.JAPAN),
         Map.entry("nissan", CarOrigin.JAPAN), Map.entry("mazda", CarOrigin.JAPAN), Map.entry("mitsubishi", CarOrigin.JAPAN),
         Map.entry("subaru", CarOrigin.JAPAN), Map.entry("hyundai", CarOrigin.KOREA), Map.entry("kia", CarOrigin.KOREA),
         Map.entry("daewoo", CarOrigin.KOREA), Map.entry("chevrolet", CarOrigin.USA), Map.entry("ford", CarOrigin.USA),
         Map.entry("mercedes-benz", CarOrigin.EUROPE), Map.entry("bmw", CarOrigin.EUROPE),
         Map.entry("volkswagen", CarOrigin.EUROPE), Map.entry("audi", CarOrigin.EUROPE), Map.entry("opel", CarOrigin.EUROPE),
         Map.entry("lada", CarOrigin.EUROPE));

   private CarOrigins() {
   }

   public static CarOrigin of(String brandSlug) {
      return BY_BRAND.getOrDefault(brandSlug, CarOrigin.UNKNOWN);
   }
}
