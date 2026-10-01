package kg.kudaibergen.catalog.importing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Формат Excel-файла каталога. Первый лист, первая строка — заголовки, дальше — строка на запчасть.
 * Строка без названия, но с маркой — ещё одна машина для запчасти выше («Подходит к машинам»).
 */
public final class PartSheet {

   /** Колонки по порядку: заголовок в шаблоне и обязательность. */
   public enum Column {
      TITLE("Название*"),
      CATEGORY("Категория*"),
      CONDITION("Состояние*"),
      PRICE("Цена, сом*"),
      QUANTITY("Количество"),
      MANUFACTURER("Производитель"),
      OEM("Номер детали"),
      BRAND("Марка*"),
      MODEL("Модель"),
      YEAR_FROM("Год от"),
      YEAR_TO("Год до");

      private final String header;

      Column(String header) {
         this.header = header;
      }

      public String header() {
         return header;
      }
   }

   /** Строка листа: номер как в Excel (с 1) и значения ячеек по колонкам, пустые — "". */
   public record SheetRow(int number, List<String> cells) {

      public String get(Column column) {
         return column.ordinal() < cells.size() ? cells.get(column.ordinal()) : "";
      }

      public boolean isBlank() {
         return cells.stream().allMatch(String::isBlank);
      }
   }

   private PartSheet() {
   }

   /** Строки данных первого листа; пустые строки пропускаются. */
   public static List<SheetRow> read(InputStream xlsx) throws IOException {
      try (Workbook workbook = new XSSFWorkbook(xlsx)) {
         Sheet sheet = workbook.getSheetAt(0);
         DataFormatter formatter = new DataFormatter();
         List<SheetRow> rows = new ArrayList<>();
         for (int index = sheet.getFirstRowNum() + 1; index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) {
               continue;
            }
            List<String> cells = new ArrayList<>();
            for (Column column : Column.values()) {
               Cell cell = row.getCell(column.ordinal());
               cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
            }
            SheetRow sheetRow = new SheetRow(index + 1, cells);
            if (!sheetRow.isBlank()) {
               rows.add(sheetRow);
            }
         }
         return rows;
      }
   }

   /**
    * Шаблон: лист «Запчасти» с заголовками и примером (две строки — одна запчасть на две машины)
    * и лист «Справочник» — допустимые категории, состояния и марки.
    */
   public static byte[] template(List<String> categories, List<String> conditions, List<String> brands) {
      try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
         CellStyle bold = workbook.createCellStyle();
         Font font = workbook.createFont();
         font.setBold(true);
         bold.setFont(font);

         Sheet parts = workbook.createSheet("Запчасти");
         Row header = parts.createRow(0);
         for (Column column : Column.values()) {
            Cell cell = header.createCell(column.ordinal());
            cell.setCellValue(column.header());
            cell.setCellStyle(bold);
            parts.setColumnWidth(column.ordinal(), 18 * 256);
         }
         parts.setColumnWidth(Column.TITLE.ordinal(), 36 * 256);
         fill(parts.createRow(1), "Стойка передняя KYB, левая", categories.isEmpty() ? "" : categories.get(0),
               conditions.get(0), "4500", "2", "KYB", "333114", "Toyota", "Camry 40", "2006", "2011");
         fill(parts.createRow(2), "", "", "", "", "", "", "", "Toyota", "Camry 50", "2011", "2017");

         Sheet reference = workbook.createSheet("Справочник");
         Row titles = reference.createRow(0);
         String[] names = {"Категории", "Состояние", "Марки"};
         for (int i = 0; i < names.length; i++) {
            Cell cell = titles.createCell(i);
            cell.setCellValue(names[i]);
            cell.setCellStyle(bold);
            reference.setColumnWidth(i, 22 * 256);
         }
         int rows = Math.max(categories.size(), Math.max(conditions.size(), brands.size()));
         for (int i = 0; i < rows; i++) {
            Row row = reference.createRow(i + 1);
            row.createCell(0).setCellValue(i < categories.size() ? categories.get(i) : "");
            row.createCell(1).setCellValue(i < conditions.size() ? conditions.get(i) : "");
            row.createCell(2).setCellValue(i < brands.size() ? brands.get(i) : "");
         }
         workbook.write(out);
         return out.toByteArray();
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось собрать шаблон", e);
      }
   }

   private static void fill(Row row, String... values) {
      for (int i = 0; i < values.length; i++) {
         row.createCell(i).setCellValue(values[i]);
      }
   }
}
