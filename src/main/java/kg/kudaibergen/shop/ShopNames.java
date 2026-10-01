package kg.kudaibergen.shop;

import java.util.Locale;
import java.util.regex.Pattern;

import kg.kudaibergen.common.error.BadRequestException;

/** Название магазина: 2–60 символов, без телефонов и ссылок (ТЗ, раздел 8). */
public final class ShopNames {

   private static final int MIN = 2;
   private static final int MAX = 60;
   private static final int PHONE_DIGITS = 7;
   private static final Pattern LINK = Pattern.compile(
         "(https?://|www\\.|t\\.me/|@|\\.(kg|ru|com|net|org|kz|uz|me)(\\b|/))");

   private ShopNames() {
   }

   public static String validate(String raw) {
      String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
      if (name.length() < MIN || name.length() > MAX) {
         throw new BadRequestException("SHOP_NAME_LENGTH", "Название — от 2 до 60 символов");
      }
      long digits = name.chars().filter(Character::isDigit).count();
      if (digits >= PHONE_DIGITS || LINK.matcher(name.toLowerCase(Locale.ROOT)).find()) {
         throw new BadRequestException("SHOP_NAME_CONTACTS", "В названии не должно быть телефонов и ссылок");
      }
      return name;
   }
}
