package kg.kudaibergen.admin.tenants;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kg.kudaibergen.market.entity.Side;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Список арендаторов рынка: xlsx (первый лист) или csv (запятая или точка с запятой, UTF-8).
 * Колонки по порядку: ряд, номер контейнера, сторона, ФИО арендатора, телефон. Первая строка — заголовок,
 * если во второй колонке не число. Сторона: С / Ю / З / В (или север, юг, запад, восток, N, S, W, E);
 * пусто — если в ряду один контейнер с таким номером.
 */
public final class TenantSheet {

   static final String[] HEADERS = {"Ряд", "Номер контейнера", "Сторона", "ФИО арендатора", "Телефон"};
   static final int MAX_ROWS = 5000;

   private TenantSheet() {
   }

   /** Строка файла как есть: line — номер строки в файле (с 1). */
   public record RawRow(int line, String row, String number, String side, String name, String phone) {
   }

   public static List<RawRow> read(InputStream in, String fileName) throws IOException {
      boolean csv = fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv");
      List<List<String>> lines = csv ? csv(in) : xlsx(in);
      List<RawRow> rows = new ArrayList<>();
      for (int i = 0; i < lines.size(); i++) {
         List<String> cells = lines.get(i);
         if (cells.stream().allMatch(String::isBlank)) {
            continue;
         }
         if (i == 0 && !cell(cells, 1).matches("\\d+")) {
            continue;
         }
         rows.add(new RawRow(i + 1, cell(cells, 0), cell(cells, 1), cell(cells, 2), cell(cells, 3), cell(cells, 4)));
      }
      return rows;
   }

   /** С / Ю / З / В и их варианты; пусто — null; не распознано — IllegalArgumentException. */
   public static Side side(String value) {
      String v = value.strip().toLowerCase(Locale.ROOT).replace(".", "");
      if (v.isEmpty()) {
         return null;
      }
      return switch (v) {
         case "с", "c", "сев", "север", "северная", "n", "north" -> Side.NORTH;
         case "ю", "юг", "южная", "s", "south" -> Side.SOUTH;
         case "з", "зап", "запад", "западная", "w", "west" -> Side.WEST;
         case "в", "вост", "восток", "восточная", "e", "east" -> Side.EAST;
         default -> throw new IllegalArgumentException(value);
      };
   }

   /** 0555 12 34 56, 555123456, 996555123456, +996 555 123 456 → +996555123456; пусто — null. */
   public static String phone(String value) {
      String digits = value.replaceAll("\\D", "");
      if (digits.isEmpty()) {
         return null;
      }
      if (digits.length() == 9) {
         return "+996" + digits;
      }
      if (digits.length() == 10 && digits.startsWith("0")) {
         return "+996" + digits.substring(1);
      }
      if (digits.length() == 12 && digits.startsWith("996")) {
         return "+" + digits;
      }
      throw new IllegalArgumentException(value);
   }

   /** «Ряд 14», «ряд Ш», «14» → «14», «ш» для сравнения с кодом ряда. */
   public static String rowKey(String value) {
      return value.strip().toLowerCase(Locale.ROOT).replaceFirst("^ряд\\s*", "").strip();
   }

   public static byte[] template() {
      try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
         CellStyle bold = workbook.createCellStyle();
         Font font = workbook.createFont();
         font.setBold(true);
         bold.setFont(font);
         Sheet sheet = workbook.createSheet("Арендаторы");
         Row header = sheet.createRow(0);
         for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(bold);
            sheet.setColumnWidth(i, (i == 3 ? 32 : 18) * 256);
         }
         String[] example = {"14", "12", "С", "Токтосунов Азамат", "+996555123456"};
         Row row = sheet.createRow(1);
         for (int i = 0; i < example.length; i++) {
            row.createCell(i).setCellValue(example[i]);
         }
         workbook.write(out);
         return out.toByteArray();
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось собрать шаблон", e);
      }
   }

   private static String cell(List<String> cells, int index) {
      return index < cells.size() ? cells.get(index).strip() : "";
   }

   private static List<List<String>> xlsx(InputStream in) throws IOException {
      try (Workbook workbook = new XSSFWorkbook(in)) {
         Sheet sheet = workbook.getSheetAt(0);
         DataFormatter formatter = new DataFormatter();
         List<List<String>> result = new ArrayList<>();
         for (int index = 0; index <= sheet.getLastRowNum() && result.size() <= MAX_ROWS; index++) {
            Row row = sheet.getRow(index);
            List<String> cells = new ArrayList<>();
            for (int c = 0; c < HEADERS.length; c++) {
               Cell cell = row == null ? null : row.getCell(c);
               cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
            }
            result.add(cells);
         }
         return result;
      }
   }

   private static List<List<String>> csv(InputStream in) throws IOException {
      List<List<String>> result = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
         String line;
         while ((line = reader.readLine()) != null && result.size() <= MAX_ROWS) {
            if (result.isEmpty() && line.startsWith("﻿")) {
               line = line.substring(1);
            }
            char separator = line.indexOf(';') >= 0 ? ';' : ',';
            result.add(split(line, separator));
         }
      }
      return result;
   }

   /** Разбор строки csv с кавычками: "Иванов, А." остаётся одним полем. */
   static List<String> split(String line, char separator) {
      List<String> cells = new ArrayList<>();
      StringBuilder current = new StringBuilder();
      boolean quoted = false;
      for (int i = 0; i < line.length(); i++) {
         char ch = line.charAt(i);
         if (ch == '"') {
            if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
               current.append('"');
               i++;
            } else {
               quoted = !quoted;
            }
         } else if (ch == separator && !quoted) {
            cells.add(current.toString());
            current.setLength(0);
         } else {
            current.append(ch);
         }
      }
      cells.add(current.toString());
      return cells;
   }
}
