package kg.kudaibergen.garage.dto;

/** Ограничения гаража (ТЗ, раздел 4.1). */
public final class CarRules {

   /** Не больше машин в гараже. */
   public static final int MAX_CARS = 10;

   /** VIN (17 знаков) или номер кузова японских авто вроде «NHW20-1234567». */
   public static final String VIN = "[A-Za-z0-9-]{5,20}";
   public static final String VIN_MESSAGE = "VIN или номер кузова: 5–20 латинских букв и цифр";

   private CarRules() {
   }
}
