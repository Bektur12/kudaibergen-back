package kg.kudaibergen.catalog.importing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import kg.kudaibergen.catalog.dto.PartInput;
import kg.kudaibergen.category.Category;
import kg.kudaibergen.garage.BrandRepository;
import kg.kudaibergen.garage.CarModelRepository;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.request.entity.PartCondition;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PartImportTest {

   private PartImportMapper mapper;

   @BeforeEach
   void setUp() {
      BrandRepository brands = mock(BrandRepository.class);
      CarModelRepository models = mock(CarModelRepository.class);
      when(brands.findAll()).thenReturn(List.of(brand(1L, "toyota", "Toyota", List.of("тойота")),
            brand(2L, "mercedes-benz", "Mercedes-Benz", List.of("мерс"))));
      when(models.findAll()).thenReturn(List.of(model(10L, 1L, "Camry", "40"), model(11L, 1L, "Camry", "50"),
            model(20L, 2L, "Sprinter", null)));
      mapper = new PartImportMapper(new VehicleDirectory(brands, models),
            List.of(category(1L, "suspension", "Ходовая", "Жүрүүчү бөлүк")));
   }

   @Test
   void строкаПродолжениеДобавляетМашинуКЗапчастиВыше() throws IOException {
      List<PartSheet.SheetRow> rows = PartSheet.read(xlsx(
            new String[]{"Стойка передняя KYB", "Ходовая", "Новое", "4 500", "2", "KYB", "333114", "Toyota", "Camry 40", "2006", "2011"},
            new String[]{"", "", "", "", "", "", "", "тойота", "camry 50", "2011", "2017"},
            new String[]{"Амортизатор Sprinter", "жүрүүчү бөлүк", "Б/У", "3000", "", "", "", "мерс", "Sprinter", "", ""}));

      PartImportMapper.Result result = mapper.map(rows);

      assertThat(result.errors()).isEmpty();
      assertThat(result.parts()).hasSize(2);
      PartInput stoika = result.parts().get(0).input();
      assertThat(stoika.price()).isEqualTo(4500);
      assertThat(stoika.condition()).isEqualTo(PartCondition.NEW);
      assertThat(stoika.fitments()).extracting(PartInput.FitmentInput::modelId).containsExactly(10L, 11L);
      PartInput sprinter = result.parts().get(1).input();
      assertThat(sprinter.quantity()).isEqualTo(1);
      assertThat(sprinter.condition()).isEqualTo(PartCondition.USED);
      assertThat(sprinter.fitments().get(0).brandId()).isEqualTo(2L);
   }

   @Test
   void ошибкиСНомеромСтрокиИПропускЗапчастиЦеликом() throws IOException {
      List<PartSheet.SheetRow> rows = PartSheet.read(xlsx(
            new String[]{"Фара", "Оптика", "Новое", "100", "", "", "", "Toyota", "", "", ""},
            new String[]{"", "", "", "", "", "", "", "Toyota", "Camry 50", "", ""},
            new String[]{"Колодки", "Ходовая", "Новое", "дорого", "", "", "", "Toyota", "", "", ""},
            new String[]{"Ступица", "Ходовая", "Новое", "900", "", "", "", "Лада", "", "", ""},
            new String[]{"Рычаг", "Ходовая", "Новое", "900", "", "", "", "Toyota", "Camry", "", ""},
            new String[]{"Шаровая", "Ходовая", "Новое", "900", "", "", "", "Toyota", "", "2015", "2010"}));

      PartImportMapper.Result result = mapper.map(rows);

      assertThat(result.parts()).isEmpty();
      assertThat(result.errors()).extracting(PartImportMapper.RowError::row).containsExactly(2, 4, 5, 6, 7);
      assertThat(result.errors().get(0).message()).contains("Категория «Оптика»");
      assertThat(result.errors().get(1).message()).contains("Цена");
      assertThat(result.errors().get(2).message()).contains("Марка «Лада»");
      assertThat(result.errors().get(3).message()).contains("неоднозначна");
      assertThat(result.errors().get(4).message()).contains("Год «от»");
   }

   @Test
   void шаблонЧитаетсяОбратно() throws IOException {
      byte[] template = PartSheet.template(List.of("Ходовая"), List.of("Новое", "Б/У", "Под заказ"),
            List.of("Toyota"));
      List<PartSheet.SheetRow> rows = PartSheet.read(new ByteArrayInputStream(template));
      assertThat(rows).hasSize(2);
      assertThat(mapper.map(rows).parts()).hasSize(1);
   }

   @Test
   void состоянияПоРусскиИКыргызски() {
      assertThat(PartImportMapper.condition("б/у")).isEqualTo(PartCondition.USED);
      assertThat(PartImportMapper.condition("Жаңы")).isEqualTo(PartCondition.NEW);
      assertThat(PartImportMapper.condition("Под заказ")).isEqualTo(PartCondition.ON_ORDER);
      assertThat(PartImportMapper.integer("4 500", "Цена")).isEqualTo(4500);
   }

   private static ByteArrayInputStream xlsx(String[]... rows) throws IOException {
      try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
         Sheet sheet = workbook.createSheet();
         sheet.createRow(0).createCell(0).setCellValue("Название*");
         for (int i = 0; i < rows.length; i++) {
            Row row = sheet.createRow(i + 1);
            for (int j = 0; j < rows[i].length; j++) {
               row.createCell(j).setCellValue(rows[i][j]);
            }
         }
         workbook.write(out);
         return new ByteArrayInputStream(out.toByteArray());
      }
   }

   private static Brand brand(Long id, String slug, String name, List<String> aliases) {
      Brand brand = BeanUtils.instantiateClass(Brand.class);
      ReflectionTestUtils.setField(brand, "id", id);
      ReflectionTestUtils.setField(brand, "slug", slug);
      ReflectionTestUtils.setField(brand, "name", name);
      ReflectionTestUtils.setField(brand, "sortOrder", (short) 1);
      ReflectionTestUtils.setField(brand, "aliases", aliases);
      return brand;
   }

   private static CarModel model(Long id, Long brandId, String name, String generation) {
      CarModel model = BeanUtils.instantiateClass(CarModel.class);
      ReflectionTestUtils.setField(model, "id", id);
      ReflectionTestUtils.setField(model, "brandId", brandId);
      ReflectionTestUtils.setField(model, "name", name);
      ReflectionTestUtils.setField(model, "generation", generation);
      ReflectionTestUtils.setField(model, "sortOrder", (short) 1);
      ReflectionTestUtils.setField(model, "aliases", List.<String>of());
      return model;
   }

   private static Category category(Long id, String slug, String ru, String kg) {
      Category category = BeanUtils.instantiateClass(Category.class);
      ReflectionTestUtils.setField(category, "id", id);
      ReflectionTestUtils.setField(category, "slug", slug);
      ReflectionTestUtils.setField(category, "nameRu", ru);
      ReflectionTestUtils.setField(category, "nameKg", kg);
      return category;
   }
}
