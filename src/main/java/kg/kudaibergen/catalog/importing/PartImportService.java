package kg.kudaibergen.catalog.importing;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import kg.kudaibergen.catalog.MyPartsService;
import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * Массовая загрузка каталога из Excel (второй этап ТЗ 9.3). Файл разбирается целиком до записи,
 * правильные строки становятся черновиками, неправильные — в отчёт с номером строки. Выкладывают
 * владелец и сотрудники, как и по одной запчасти.
 */
@Service
public class PartImportService {

   static final int MAX_PARTS = 500;
   private static final DataSize MAX_FILE = DataSize.ofMegabytes(5);

   private final ShopAccess access;
   private final MyPartsService parts;
   private final VehicleDirectory directory;
   private final CategoryService categories;

   public PartImportService(ShopAccess access, MyPartsService parts, VehicleDirectory directory,
                            CategoryService categories) {
      this.access = access;
      this.parts = parts;
      this.directory = directory;
      this.categories = categories;
   }

   @Transactional
   public ImportReportDto importXlsx(Long userId, MultipartFile file) {
      Shop shop = access.requireMember(userId).shop();
      if (file == null || file.isEmpty()) {
         throw new BadRequestException("FILE_REQUIRED", "Файл не передан");
      }
      String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
      if (!name.endsWith(".xlsx")) {
         throw new BadRequestException("BAD_EXCEL", "Нужен файл Excel .xlsx — скачайте шаблон");
      }
      if (file.getSize() > MAX_FILE.toBytes()) {
         throw new BadRequestException("FILE_TOO_LARGE", "Файл больше 5 МБ");
      }
      List<PartSheet.SheetRow> rows;
      try (InputStream content = file.getInputStream()) {
         rows = PartSheet.read(content);
      } catch (IOException | RuntimeException e) {
         throw new BadRequestException("BAD_EXCEL", "Не удалось прочитать файл — сохраните его как .xlsx по шаблону");
      }
      long partRows = rows.stream().filter(row -> !row.get(PartSheet.Column.TITLE).isBlank()).count();
      if (partRows > MAX_PARTS) {
         throw new BadRequestException("TOO_MANY_ROWS", "Не больше " + MAX_PARTS + " запчастей за один файл");
      }
      PartImportMapper.Result result = new PartImportMapper(directory, categories.all()).map(rows);
      List<Long> ids = new ArrayList<>();
      for (PartImportMapper.ImportedPart part : result.parts()) {
         ids.add(parts.createImported(shop.getId(), part.input()));
      }
      return new ImportReportDto(ids.size(), ids, result.errors());
   }

   /** Шаблон с примером и справочником категорий, состояний и марок на языке продавца. */
   public byte[] template(Lang lang) {
      List<String> categoryNames = categories.all().stream().map(category -> category.name(lang)).toList();
      List<String> conditions = lang == Lang.KG ? Arrays.asList("Жаңы", "Колдонулган", "Заказ менен")
            : Arrays.asList("Новое", "Б/У", "Под заказ");
      List<String> brands = directory.brands().stream().map(Brand::getName).toList();
      return PartSheet.template(categoryNames, conditions, brands);
   }
}
