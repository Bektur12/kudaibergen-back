package kg.kudaibergen.auth.dto;

/** Телефон в E.164, только Кыргызстан: +996 и 9 цифр. */
public final class PhoneFormat {

   public static final String E164_KG = "\\+996\\d{9}";
   public static final String MESSAGE = "Номер в формате +996XXXXXXXXX";

   private PhoneFormat() {
   }
}
