package kg.kudaibergen.catalog;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.catalog.dto.CarFilter;
import kg.kudaibergen.catalog.entity.Fitment;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.request.entity.PartCondition;
import kg.kudaibergen.user.entity.Lang;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRulesTest {

   private static final long TOYOTA = 1;
   private static final long LEXUS = 2;
   private static final long CAMRY_40 = 11;
   private static final long CAMRY_50 = 12;
   private static final long CAMRY_70 = 13;

   /** Критерий приёмки ТЗ (этап 2): «Camry 40–50» находит Camry 50 · 2012 и не подходит Camry 70. */
   @Test
   void деталь_Camry40_50_подходитCamry50_ноНеCamry70() {
      List<Fitment> camry40to50 = List.of(new Fitment(TOYOTA, CAMRY_40, 2006, 2011),
            new Fitment(TOYOTA, CAMRY_50, 2011, 2017));
      assertThat(Fits.anyFits(camry40to50, car(TOYOTA, CAMRY_50, 2012))).isTrue();
      assertThat(Fits.anyExact(camry40to50, car(TOYOTA, CAMRY_50, 2012))).isTrue();
      assertThat(Fits.anyFits(camry40to50, car(TOYOTA, CAMRY_70, 2019))).isFalse();
      // год вне диапазона поколения
      assertThat(Fits.anyFits(camry40to50, car(TOYOTA, CAMRY_50, 2019))).isFalse();
   }

   @Test
   void всеМоделиМаркиПодходятНоНеТочно() {
      List<Fitment> anyToyota = List.of(new Fitment(TOYOTA, null, null, null));
      assertThat(Fits.anyFits(anyToyota, car(TOYOTA, CAMRY_70, 2020))).isTrue();
      assertThat(Fits.anyExact(anyToyota, car(TOYOTA, CAMRY_70, 2020))).isFalse();
      assertThat(Fits.anyFits(anyToyota, car(LEXUS, 99L, 2020))).isFalse();
   }

   @Test
   void поискПоМаркеБезМоделиИГода() {
      List<Fitment> camry50 = List.of(new Fitment(TOYOTA, CAMRY_50, 2011, 2017));
      assertThat(Fits.anyFits(camry50, new CarFilter(TOYOTA, null, null, "Toyota"))).isTrue();
      assertThat(Fits.anyFits(camry50, new CarFilter(TOYOTA, null, 2020, "Toyota · 2020"))).isFalse();
   }

   @Test
   void открытыеГраницыЛет() {
      List<Fitment> fromOnly = List.of(new Fitment(TOYOTA, CAMRY_50, 2011, null));
      assertThat(Fits.anyFits(fromOnly, car(TOYOTA, CAMRY_50, 2030))).isTrue();
      assertThat(Fits.anyFits(fromOnly, car(TOYOTA, CAMRY_50, 2010))).isFalse();
   }

   @Test
   void словаЗапросаИСинонимы() {
      assertThat(PartSearch.tokens("Стойка передняя KYB, левая!")).containsExactly("стойка", "передняя", "kyb", "левая");
      assertThat(PartSearch.tokens("  ... ")).isEmpty();
      assertThat(PartSearch.tokens("ёлка")).containsExactly("елка");

      Map<String, Set<String>> alternatives = new LinkedHashMap<>();
      alternatives.put("стойки", new LinkedHashSet<>(List.of("стойки", "амортизатор")));
      alternatives.put("kyb", new LinkedHashSet<>(List.of("kyb")));
      assertThat(PartSearch.tsQuery(alternatives)).isEqualTo("(стойки:* | амортизатор:*) & (kyb:*)");
   }

   @Test
   void номерДетали() {
      assertThat(PartSearch.oemPrefix("90919-01253")).isEqualTo("9091901253");
      assertThat(PartSearch.oemPrefix("kyb")).isNull();
      assertThat(PartSearch.oemPrefix("a1")).isNull();
      assertThat(Part.normalizeOem(" 48510-06420 ")).isEqualTo("4851006420");
      assertThat(Part.normalizeOem("--")).isNull();
   }

   @Test
   void безОбязательныхПолейНеПубликуется() {
      Part part = new Part(1L);
      assertThat(MyPartsService.missing(part))
            .containsExactly("title", "categoryId", "condition", "price", "mediaIds", "fitments");
      part.setTitle("Стойка передняя KYB");
      part.setCategoryId(1L);
      part.setCondition(PartCondition.NEW);
      part.setPrice(4500);
      part.replacePhotos(List.of(10L));
      part.replaceFitments(List.of(new Fitment(TOYOTA, CAMRY_50, 2011, 2017)));
      assertThat(MyPartsService.missing(part)).isEmpty();
      part.setTitle("Ст");
      assertThat(MyPartsService.missing(part)).containsExactly("title");
   }

   @Test
   void подписьЛетВЧипе() {
      assertThat(CatalogView.years(2011, 2017, Lang.RU)).isEqualTo(" · 2011–2017");
      assertThat(CatalogView.years(2011, null, Lang.RU)).isEqualTo(" · с 2011");
      assertThat(CatalogView.years(null, 2017, Lang.KG)).isEqualTo(" · 2017 жылга чейин");
      assertThat(CatalogView.years(null, null, Lang.RU)).isEmpty();
   }

   private static CarFilter car(long brand, Long model, int year) {
      return new CarFilter(brand, model, year, "car");
   }
}
