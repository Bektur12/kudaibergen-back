package kg.kudaibergen.garage;

import java.util.List;

import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VehicleDirectoryTest {

   private VehicleDirectory directory;

   @BeforeEach
   void setUp() {
      Brand toyota = brand(1L, "toyota", "Toyota", true, 1, List.of("тойота"));
      Brand mercedes = brand(2L, "mercedes-benz", "Mercedes-Benz", true, 3, List.of("мерс", "мерседес"));
      Brand audi = brand(3L, "audi", "Audi", false, 100, List.of("ауди"));
      Brand bmw = brand(4L, "bmw", "BMW", true, 4, List.of("бэха"));
      Brand honda = brand(5L, "honda", "Honda", false, 100, List.of("хонда"));

      BrandRepository brands = mock(BrandRepository.class);
      CarModelRepository models = mock(CarModelRepository.class);
      when(brands.findAll()).thenReturn(List.of(audi, bmw, honda, mercedes, toyota));
      when(models.findAll()).thenReturn(List.of(
            model(10L, 1L, "Camry", "40", 2006, 2011, 2, List.of("камри")),
            model(11L, 1L, "Camry", "50", 2011, 2017, 3, List.of("камри", "xv50")),
            model(12L, 1L, "Prius", "30", 2009, 2015, 21, List.of("приус")),
            model(20L, 2L, "E-класс", "W211", 2002, 2009, 11, List.of("ешка")),
            model(21L, 2L, "Sprinter", null, 2006, 2018, 50, List.of("спринтер")),
            model(50L, 5L, "CR-V", "RE", 2006, 2011, 10, List.of("црв"))));
      directory = new VehicleDirectory(brands, models);
   }

   @Test
   void популярныеМаркиПервымиОстальныеПоАлфавиту() {
      assertThat(directory.brands()).extracting(Brand::getName)
            .containsExactly("Toyota", "Mercedes-Benz", "BMW", "Audi", "Honda");
   }

   @Test
   void поискМаркиПоНародномуНазваниюИПоМодели() {
      assertThat(directory.searchBrands("мерс")).extracting(Brand::getSlug).containsExactly("mercedes-benz");
      assertThat(directory.searchBrands("Бэха")).extracting(Brand::getSlug).containsExactly("bmw");
      assertThat(directory.searchBrands("камри")).extracting(Brand::getSlug).containsExactly("toyota");
      assertThat(directory.searchBrands("")).hasSize(5);
      assertThat(directory.searchBrands("жигули")).isEmpty();
   }

   @Test
   void поискМоделиПоСловамИзРазныхПолей() {
      assertThat(directory.searchModels("камри 50", null, 30)).extracting(CarModel::getId).containsExactly(11L);
      assertThat(directory.searchModels("камри", null, 30)).extracting(CarModel::getId).containsExactly(10L, 11L);
      assertThat(directory.searchModels("мерс w211", null, 30)).extracting(CarModel::getId).containsExactly(20L);
      assertThat(directory.searchModels("E класс", null, 30)).extracting(CarModel::getId).containsExactly(20L);
      assertThat(directory.searchModels("crv", null, 30)).extracting(CarModel::getId).containsExactly(50L);
      assertThat(directory.searchModels(null, 2L, 30)).extracting(CarModel::getId).containsExactly(20L, 21L);
   }

   @Test
   void моделиЧужойИНеизвестнойМарки() {
      assertThat(directory.modelsOf(3L)).isEmpty();
      assertThatThrownBy(() -> directory.modelsOf(99L)).isInstanceOf(NotFoundException.class);
      assertThatThrownBy(() -> directory.model(99L)).isInstanceOf(NotFoundException.class);
   }

   @Test
   void подписьИГодыМодели() {
      CarModel camry = directory.model(11L);
      assertThat(camry.label()).isEqualTo("Camry 50");
      assertThat(camry.covers(2012)).isTrue();
      assertThat(camry.covers(2018)).isFalse();
      assertThat(directory.model(21L).label()).isEqualTo("Sprinter");
   }

   private static Brand brand(Long id, String slug, String name, boolean popular, int sort, List<String> aliases) {
      Brand brand = BeanUtils.instantiateClass(Brand.class);
      ReflectionTestUtils.setField(brand, "id", id);
      ReflectionTestUtils.setField(brand, "slug", slug);
      ReflectionTestUtils.setField(brand, "name", name);
      ReflectionTestUtils.setField(brand, "popular", popular);
      ReflectionTestUtils.setField(brand, "sortOrder", (short) sort);
      ReflectionTestUtils.setField(brand, "aliases", aliases);
      return brand;
   }

   private static CarModel model(Long id, Long brandId, String name, String generation, int from, int to, int sort,
                                 List<String> aliases) {
      CarModel model = BeanUtils.instantiateClass(CarModel.class);
      ReflectionTestUtils.setField(model, "id", id);
      ReflectionTestUtils.setField(model, "brandId", brandId);
      ReflectionTestUtils.setField(model, "name", name);
      ReflectionTestUtils.setField(model, "generation", generation);
      ReflectionTestUtils.setField(model, "yearFrom", (short) from);
      ReflectionTestUtils.setField(model, "yearTo", (short) to);
      ReflectionTestUtils.setField(model, "sortOrder", (short) sort);
      ReflectionTestUtils.setField(model, "aliases", aliases);
      return model;
   }
}
