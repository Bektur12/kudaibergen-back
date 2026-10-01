package kg.kudaibergen.admin.tenants;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.admin.tenants.TenantImportDtos.TenantChange;
import kg.kudaibergen.admin.tenants.TenantImportDtos.TenantImportDto;
import kg.kudaibergen.admin.tenants.TenantImportDtos.TenantImportError;
import kg.kudaibergen.admin.tenants.TenantSheet.RawRow;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.market.ContainerRepository;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.market.MarketSnapshot.ContainerView;
import kg.kudaibergen.market.MarketSnapshot.RowView;
import kg.kudaibergen.market.entity.Side;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Импорт списка арендаторов [A2]: файл разбирается и сверяется со схемой рынка (предпросмотр с отчётом
 * об ошибках), применяется отдельным шагом. Пустая ячейка имени или телефона значение не стирает.
 */
@Service
public class TenantImportService {

   static final Duration PREVIEW_TTL = Duration.ofHours(24);
   static final int CHANGES_SHOWN = 200;
   static final int MAX_NAME = 120;

   private final TenantImportRepository imports;
   private final ContainerRepository containers;
   private final MarketMapService market;
   private final ObjectMapper json;

   public TenantImportService(TenantImportRepository imports, ContainerRepository containers, MarketMapService market,
                              ObjectMapper json) {
      this.imports = imports;
      this.containers = containers;
      this.market = market;
      this.json = json;
   }

   /** Разобранная строка, готовая к применению. new* = null — не менять. */
   record TenantImportRow(int line, Long containerId, String label, Side side, String oldName, String newName,
                          String oldPhone, String newPhone) {

      boolean changes() {
         return (newName != null && !newName.equals(oldName)) || (newPhone != null && !newPhone.equals(oldPhone));
      }
   }

   @Transactional
   public TenantImportDto preview(InputStream file, String fileName, Long adminId) {
      List<RawRow> raw;
      try {
         raw = TenantSheet.read(file, fileName);
      } catch (IOException | RuntimeException e) {
         throw new BadRequestException("BAD_FILE", "Не удалось прочитать файл: нужен .xlsx или .csv");
      }
      if (raw.isEmpty()) {
         throw new BadRequestException("EMPTY_FILE", "В файле нет строк с арендаторами");
      }
      if (raw.size() > TenantSheet.MAX_ROWS) {
         throw new BadRequestException("FILE_TOO_LARGE", "Не больше " + TenantSheet.MAX_ROWS + " строк за раз");
      }
      MarketSnapshot snapshot = market.snapshot();
      List<TenantImportRow> rows = new ArrayList<>();
      List<TenantImportError> errors = new ArrayList<>();
      Set<Long> seen = new HashSet<>();
      for (RawRow row : raw) {
         try {
            TenantImportRow parsed = resolve(row, snapshot);
            if (!seen.add(parsed.containerId())) {
               throw new IllegalStateException("Контейнер " + parsed.label() + " уже был выше в файле");
            }
            rows.add(parsed);
         } catch (IllegalStateException e) {
            errors.add(new TenantImportError(row.line(), e.getMessage()));
         }
      }
      TenantImport saved = imports.save(new TenantImport(fileName == null ? "tenants" : fileName, adminId, raw.size(),
            rows.size(), errors.size(), write(rows), write(errors)));
      return dto(saved, rows, errors);
   }

   @Transactional(readOnly = true)
   public TenantImportDto get(Long importId) {
      TenantImport found = require(importId);
      return dto(found, rows(found), errors(found));
   }

   @Transactional
   public TenantImportDto apply(Long importId) {
      TenantImport found = require(importId);
      if (found.isApplied()) {
         throw new ConflictException("IMPORT_APPLIED", "Этот файл уже применён");
      }
      if (found.getCreatedAt().plus(PREVIEW_TTL).isBefore(Instant.now())) {
         throw new ConflictException("IMPORT_EXPIRED", "Предпросмотр устарел — загрузите файл заново");
      }
      List<TenantImportRow> rows = rows(found);
      for (TenantImportRow row : rows) {
         if (!row.changes()) {
            continue;
         }
         containers.findById(row.containerId()).ifPresent(container -> {
            if (row.newName() != null) {
               container.setTenantName(row.newName());
            }
            if (row.newPhone() != null) {
               container.setTenantPhone(row.newPhone());
            }
         });
      }
      containers.flush();
      found.applied();
      market.reload();
      return dto(found, rows, errors(found));
   }

   static TenantImportRow resolve(RawRow row, MarketSnapshot snapshot) {
      if (row.row().isBlank() || row.number().isBlank()) {
         throw new IllegalStateException("Укажите ряд и номер контейнера");
      }
      String key = TenantSheet.rowKey(row.row());
      RowView rowView = snapshot.rows().stream()
            .filter(view -> view.row().getCode().toLowerCase(Locale.ROOT).equals(key)
                  || TenantSheet.rowKey(view.row().getLabel()).equals(key))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Нет ряда «" + row.row() + "»"));
      int number;
      try {
         number = Integer.parseInt(row.number().replaceAll("\\s", ""));
      } catch (NumberFormatException e) {
         throw new IllegalStateException("Номер контейнера — число: «" + row.number() + "»");
      }
      Side side;
      try {
         side = TenantSheet.side(row.side());
      } catch (IllegalArgumentException e) {
         throw new IllegalStateException("Сторона — С, Ю, З или В: «" + row.side() + "»");
      }
      List<ContainerView> matches = rowView.all().stream()
            .filter(view -> view.container().getNumber() == number)
            .filter(view -> side == null || view.container().getSide() == side)
            .toList();
      if (matches.isEmpty()) {
         throw new IllegalStateException("В ряду «" + rowView.row().getLabel() + "» нет контейнера " + number
               + (side == null ? "" : " на этой стороне"));
      }
      if (matches.size() > 1) {
         throw new IllegalStateException("Контейнер " + number + " есть на двух сторонах ряда — укажите сторону");
      }
      String name = row.name().isBlank() ? null : row.name().strip().replaceAll("\\s+", " ");
      if (name != null && name.length() > MAX_NAME) {
         throw new IllegalStateException("ФИО длиннее " + MAX_NAME + " символов");
      }
      String phone;
      try {
         phone = TenantSheet.phone(row.phone());
      } catch (IllegalArgumentException e) {
         throw new IllegalStateException("Телефон не похож на номер +996XXXXXXXXX: «" + row.phone() + "»");
      }
      if (name == null && phone == null) {
         throw new IllegalStateException("Нет ни ФИО, ни телефона арендатора");
      }
      ContainerView container = matches.get(0);
      return new TenantImportRow(row.line(), container.container().getId(),
            rowView.row().getLabel() + " · " + number, container.container().getSide(),
            container.container().getTenantName(), name, container.container().getTenantPhone(), phone);
   }

   private TenantImportDto dto(TenantImport found, List<TenantImportRow> rows, List<TenantImportError> errors) {
      List<TenantImportRow> changed = rows.stream().filter(TenantImportRow::changes).toList();
      List<TenantChange> shown = changed.stream().limit(CHANGES_SHOWN)
            .map(row -> new TenantChange(row.line(), row.containerId(), row.label(), row.side(), row.oldName(),
                  row.newName(), row.oldPhone(), row.newPhone()))
            .toList();
      return new TenantImportDto(found.getId(), found.getFileName(), found.getStatus(), found.getRowsTotal(),
            found.getRowsOk(), found.getRowsError(), changed.size(), shown, errors, found.getCreatedAt(),
            found.getAppliedAt());
   }

   private TenantImport require(Long importId) {
      return imports.findById(importId)
            .orElseThrow(() -> new NotFoundException("IMPORT_NOT_FOUND", "Импорт не найден"));
   }

   private List<TenantImportRow> rows(TenantImport found) {
      return read(found.getRows(), new TypeReference<>() { });
   }

   private List<TenantImportError> errors(TenantImport found) {
      return read(found.getErrors(), new TypeReference<>() { });
   }

   private <T> T read(String value, TypeReference<T> type) {
      try {
         return json.readValue(value, type);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException("Импорт арендаторов: испорчены сохранённые строки", e);
      }
   }

   private String write(Object value) {
      try {
         return json.writeValueAsString(value);
      } catch (JsonProcessingException e) {
         throw new IllegalStateException(e);
      }
   }
}
