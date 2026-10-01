package kg.kudaibergen.catalog.importing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kg.kudaibergen.catalog.dto.PartInput;
import kg.kudaibergen.catalog.importing.PartSheet.Column;
import kg.kudaibergen.catalog.importing.PartSheet.SheetRow;
import kg.kudaibergen.category.Category;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.request.entity.PartCondition;

/**
 * Строки Excel → запчасти. Названия категорий, состояний, марок и моделей понимаются по-русски
 * и по-кыргызски, как в приложении («Ходовая», «Б/У», «мерс», «Camry 50»). Ошибочная строка не
 * останавливает импорт: запчасть с ошибкой пропускается вместе со своими строками-продолжениями.
 */
public class PartImportMapper {

   static final int MAX_FITMENTS = 20;

   private final VehicleDirectory directory;
   private final Map<String, Long> categoryIds = new HashMap<>();

   public PartImportMapper(VehicleDirectory directory, List<Category> categories) {
      this.directory = directory;
      for (Category category : categories) {
         categoryIds.put(key(category.getNameRu()), category.getId());
         categoryIds.put(key(category.getNameKg()), category.getId());
         categoryIds.put(key(category.getSlug()), category.getId());
      }
   }

   /** Запчасть из файла: номер первой строки и форма, как из приложения. */
   public record ImportedPart(int row, PartInput input) {
   }

   public record RowError(int row, String message) {
   }

   public record Result(List<ImportedPart> parts, List<RowError> errors) {
   }

   public Result map(List<SheetRow> rows) {
      List<ImportedPart> parts = new ArrayList<>();
      List<RowError> errors = new ArrayList<>();
      Draft current = null;
      for (SheetRow row : rows) {
         boolean continuation = row.get(Column.TITLE).isBlank();
         if (continuation) {
            if (current == null) {
               errors.add(new RowError(row.number(), "Нет названия запчасти"));
            } else if (!current.failed) {
               try {
                  current.fitments.add(fitment(row));
               } catch (RowException ex) {
                  current.failed = true;
                  errors.add(new RowError(row.number(), ex.getMessage()));
               }
            }
            continue;
         }
         finish(current, parts, errors);
         current = new Draft(row.number());
         try {
            current.input = base(row);
            current.fitments.add(fitment(row));
         } catch (RowException ex) {
            current.failed = true;
            errors.add(new RowError(row.number(), ex.getMessage()));
         }
      }
      finish(current, parts, errors);
      return new Result(parts, errors);
   }

   private static void finish(Draft draft, List<ImportedPart> parts, List<RowError> errors) {
      if (draft == null || draft.failed) {
         return;
      }
      if (draft.fitments.size() > MAX_FITMENTS) {
         errors.add(new RowError(draft.row, "Не больше " + MAX_FITMENTS + " машин у одной запчасти"));
         return;
      }
      PartInput base = draft.input;
      parts.add(new ImportedPart(draft.row, new PartInput(base.title(), base.categoryId(), base.condition(),
            base.price(), base.quantity(), base.manufacturer(), base.oemNumber(), null, null, List.of(),
            List.copyOf(draft.fitments))));
   }

   private PartInput base(SheetRow row) {
      String title = row.get(Column.TITLE);
      if (title.length() < 3 || title.length() > 120) {
         throw new RowException("Название — от 3 до 120 символов");
      }
      Long categoryId = categoryIds.get(key(row.get(Column.CATEGORY)));
      if (categoryId == null) {
         throw new RowException("Категория «" + row.get(Column.CATEGORY) + "» не найдена — см. лист «Справочник»");
      }
      PartCondition condition = condition(row.get(Column.CONDITION));
      int price = integer(row.get(Column.PRICE), "Цена");
      if (price <= 0) {
         throw new RowException("Цена — целое число сом больше нуля");
      }
      String quantityText = row.get(Column.QUANTITY);
      int quantity = quantityText.isBlank() ? 1 : integer(quantityText, "Количество");
      if (quantity < 0) {
         throw new RowException("Количество не может быть отрицательным");
      }
      String manufacturer = limited(row.get(Column.MANUFACTURER), 60, "Производитель");
      String oem = limited(row.get(Column.OEM), 40, "Номер детали");
      return new PartInput(title, categoryId, condition, price, quantity, manufacturer, oem, null, null, null, null);
   }

   private PartInput.FitmentInput fitment(SheetRow row) {
      String brandText = row.get(Column.BRAND);
      if (brandText.isBlank()) {
         throw new RowException("Укажите марку машины");
      }
      Brand brand = brand(brandText);
      String modelText = row.get(Column.MODEL);
      Long modelId = modelText.isBlank() ? null : model(brand, modelText).getId();
      Integer from = year(row.get(Column.YEAR_FROM));
      Integer to = year(row.get(Column.YEAR_TO));
      if (from != null && to != null && from > to) {
         throw new RowException("Год «от» больше года «до»");
      }
      return new PartInput.FitmentInput(brand.getId(), modelId, from, to);
   }

   /** Точное название, slug или народное название; иначе — единственная марка по поиску. */
   private Brand brand(String text) {
      String wanted = key(text);
      for (Brand brand : directory.brands()) {
         if (key(brand.getName()).equals(wanted) || key(brand.getSlug()).equals(wanted)
               || brand.getAliases().stream().anyMatch(alias -> key(alias).equals(wanted))) {
            return brand;
         }
      }
      List<Brand> found = directory.searchBrands(text);
      if (found.size() == 1) {
         return found.get(0);
      }
      throw new RowException("Марка «" + text + "» не найдена — см. лист «Справочник»");
   }

   /** «Camry 50», «E-класс W211», «Sprinter»; иначе — единственная модель марки по поиску. */
   private CarModel model(Brand brand, String text) {
      String wanted = key(text);
      List<CarModel> models = directory.modelsOf(brand.getId());
      for (CarModel model : models) {
         if (key(model.label()).equals(wanted)) {
            return model;
         }
      }
      List<CarModel> byName = models.stream().filter(model -> key(model.getName()).equals(wanted)).toList();
      if (byName.size() == 1) {
         return byName.get(0);
      }
      List<CarModel> found = directory.searchModels(text, brand.getId(), 2);
      if (found.size() == 1) {
         return found.get(0);
      }
      throw new RowException(byName.size() > 1 || found.size() > 1
            ? "Модель «" + text + "» у " + brand.getName() + " неоднозначна — укажите поколение, например «Camry 50»"
            : "Модель «" + text + "» у " + brand.getName() + " не найдена");
   }

   static PartCondition condition(String text) {
      return switch (key(text).replace("/", "").replace(" ", "")) {
         case "новое", "новый", "жаңы", "new" -> PartCondition.NEW;
         case "бу", "бывшийвупотреблении", "колдонулган", "used" -> PartCondition.USED;
         case "подзаказ", "заказменен", "onorder", "on_order" -> PartCondition.ON_ORDER;
         default -> throw new RowException("Состояние — «Новое», «Б/У» или «Под заказ»");
      };
   }

   private static Integer year(String text) {
      if (text.isBlank()) {
         return null;
      }
      int year = integer(text, "Год");
      if (year < 1950 || year > 2100) {
         throw new RowException("Год — от 1950 до 2100");
      }
      return year;
   }

   /** Целое число: «4 500», «4500», «4500.0» из Excel. */
   static int integer(String text, String field) {
      String digits = text.replace(" ", "").replace(" ", "");
      if (digits.endsWith(".0") || digits.endsWith(",0")) {
         digits = digits.substring(0, digits.length() - 2);
      }
      try {
         return Integer.parseInt(digits);
      } catch (NumberFormatException e) {
         throw new RowException(field + " — целое число, а не «" + text + "»");
      }
   }

   private static String limited(String text, int max, String field) {
      if (text.isBlank()) {
         return null;
      }
      if (text.length() > max) {
         throw new RowException(field + " — до " + max + " символов");
      }
      return text;
   }

   private static String key(String text) {
      return text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ");
   }

   private static final class Draft {
      private final int row;
      private PartInput input;
      private final List<PartInput.FitmentInput> fitments = new ArrayList<>();
      private boolean failed;

      private Draft(int row) {
         this.row = row;
      }
   }

   private static final class RowException extends RuntimeException {
      private RowException(String message) {
         super(message, null, false, false);
      }
   }
}
