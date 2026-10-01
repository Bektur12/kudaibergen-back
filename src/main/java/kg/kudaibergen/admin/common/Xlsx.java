package kg.kudaibergen.admin.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Выгрузки админки в Excel: листы с жирной шапкой, даты по Бишкеку. Телефоны в строки кладутся уже
 * с учётом права PII_VIEW (Phones.forViewer).
 */
public final class Xlsx {

   public static final MediaType XLSX =
         MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
   static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
   static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(BISHKEK);

   private final List<SheetData> sheets = new ArrayList<>();

   private record SheetData(String name, List<String> headers, List<List<Object>> rows) {
   }

   public Xlsx sheet(String name, List<String> headers, List<List<Object>> rows) {
      sheets.add(new SheetData(name, headers, rows));
      return this;
   }

   public byte[] bytes() {
      try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
         CellStyle bold = workbook.createCellStyle();
         Font font = workbook.createFont();
         font.setBold(true);
         bold.setFont(font);
         for (SheetData data : sheets) {
            Sheet sheet = workbook.createSheet(data.name());
            Row header = sheet.createRow(0);
            for (int i = 0; i < data.headers().size(); i++) {
               Cell cell = header.createCell(i);
               cell.setCellValue(data.headers().get(i));
               cell.setCellStyle(bold);
               sheet.setColumnWidth(i, Math.min(60, Math.max(12, data.headers().get(i).length() + 4)) * 256);
            }
            sheet.createFreezePane(0, 1);
            int r = 1;
            for (List<Object> values : data.rows()) {
               Row row = sheet.createRow(r++);
               for (int i = 0; i < values.size(); i++) {
                  set(row.createCell(i), values.get(i));
               }
            }
         }
         workbook.write(out);
         workbook.dispose();
         return out.toByteArray();
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось собрать xlsx", e);
      }
   }

   public ResponseEntity<byte[]> response(String fileName) {
      String name = fileName + "-" + LocalDate.now(BISHKEK) + ".xlsx";
      return ResponseEntity.ok()
            .contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
            .body(bytes());
   }

   private static void set(Cell cell, Object value) {
      if (value == null) {
         cell.setBlank();
      } else if (value instanceof Number number && !(value instanceof BigDecimal)) {
         cell.setCellValue(number.doubleValue());
      } else if (value instanceof BigDecimal decimal) {
         cell.setCellValue(decimal.doubleValue());
      } else if (value instanceof Boolean flag) {
         cell.setCellValue(flag ? "да" : "нет");
      } else if (value instanceof Instant instant) {
         cell.setCellValue(DATE_TIME.format(instant));
      } else {
         cell.setCellValue(String.valueOf(value));
      }
   }
}
