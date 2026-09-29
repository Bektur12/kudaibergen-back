package kg.kudaibergen.market.route;

import java.util.List;

import kg.kudaibergen.user.entity.Lang;

/**
 * Имя прохода для шагов маршрута. Хранится структурой ({"kind":"BETWEEN","rows":["16","14"]}),
 * а текст собирается на языке пользователя: «между рядами 16 и 14» / «16 жана 14 катарлардын ортосуна».
 */
public record PassageName(PassageKind kind, List<String> rows) {

   public enum PassageKind {
      CENTRAL, BETWEEN, ALONG, WEST, ROW_ENDS, PARKING, SERVICE, OTHER
   }

   public static final PassageName UNKNOWN = new PassageName(PassageKind.OTHER, List.of());

   public PassageName {
      kind = kind == null ? PassageKind.OTHER : kind;
      rows = rows == null ? List.of() : List.copyOf(rows);
   }

   /** «по центральному проходу» — для «Прямо …». */
   public String along(Lang lang) {
      return switch (lang) {
         case RU -> switch (kind) {
            case CENTRAL -> "по центральному проходу";
            case BETWEEN -> "по проходу между рядами " + pair("и");
            case ALONG -> "по проходу вдоль " + (rows.size() == 1 ? "ряда " : "рядов ") + pair("и");
            case WEST -> "по западному проходу";
            case ROW_ENDS -> "по проходу у торцов рядов";
            case PARKING -> "по проходу у парковки";
            case SERVICE -> "по проходу у СТО";
            case OTHER -> "по проходу";
         };
         case KG -> switch (kind) {
            case CENTRAL -> "борбордук өтмөк менен";
            case BETWEEN -> pair("жана") + " катарлардын ортосундагы өтмөк менен";
            case ALONG -> pair("жана") + (rows.size() == 1 ? " катарынын" : " катарларынын") + " бойундагы өтмөк менен";
            case WEST -> "батыш өтмөк менен";
            case ROW_ENDS -> "катарлардын учундагы өтмөк менен";
            case PARKING -> "унаа токтоочу жайдын жанындагы өтмөк менен";
            case SERVICE -> "СТОнун жанындагы өтмөк менен";
            case OTHER -> "өтмөк менен";
         };
      };
   }

   /** «между рядами 16 и 14» — для «Направо — …». */
   public String into(Lang lang) {
      return switch (lang) {
         case RU -> switch (kind) {
            case CENTRAL -> "на центральный проход";
            case BETWEEN -> "между рядами " + pair("и");
            case ALONG -> "вдоль " + (rows.size() == 1 ? "ряда " : "рядов ") + pair("и");
            case WEST -> "на западный проход";
            case ROW_ENDS -> "в проход у торцов рядов";
            case PARKING -> "к парковке";
            case SERVICE -> "к СТО и мастерским";
            case OTHER -> "в проход";
         };
         case KG -> switch (kind) {
            case CENTRAL -> "борбордук өтмөккө";
            case BETWEEN -> pair("жана") + " катарлардын ортосуна";
            case ALONG -> pair("жана") + (rows.size() == 1 ? " катарынын" : " катарларынын") + " бойуна";
            case WEST -> "батыш өтмөккө";
            case ROW_ENDS -> "катарлардын учуна";
            case PARKING -> "унаа токтоочу жайга";
            case SERVICE -> "СТОго";
            case OTHER -> "өтмөккө";
         };
      };
   }

   private String pair(String and) {
      if (rows.isEmpty()) {
         return "";
      }
      if (rows.size() == 1) {
         return rows.get(0);
      }
      return String.join(", ", rows.subList(0, rows.size() - 1)) + " " + and + " " + rows.get(rows.size() - 1);
   }
}
