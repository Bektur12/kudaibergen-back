package kg.kudaibergen.ocr;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OemExtractorTest {

   @Test
   void номерToyotaСНаклейки() {
      List<OemExtractor.Candidate> found = OemExtractor.extract("""
            TOYOTA GENUINE PARTS
            48510-06420
            MADE IN JAPAN
            QTY 1""");
      assertThat(found).extracting(OemExtractor.Candidate::normalized).containsExactly("4851006420");
      assertThat(found.get(0).display()).isEqualTo("48510-06420");
   }

   @Test
   void номерСПробеламиИБуквами() {
      assertThat(OemExtractor.extract("Mercedes-Benz A 211 880 09 60"))
            .extracting(OemExtractor.Candidate::normalized).contains("A2118800960");
      assertThat(OemExtractor.extract("BMW 31 12 6 771 894"))
            .extracting(OemExtractor.Candidate::normalized).contains("31126771894");
   }

   @Test
   void кириллицаПохожаяНаЛатиницуИсправляется() {
      // OCR прочитал латинскую A как кириллическую А
      assertThat(OemExtractor.extract("А2118800960")).extracting(OemExtractor.Candidate::normalized)
            .containsExactly("A2118800960");
   }

   @Test
   void датыТелефоныИШтрихкодыОтбрасываются() {
      assertThat(OemExtractor.extract("""
            12.05.2023
            +996 555 123 456
            4607001234567
            KYB""")).isEmpty();
      assertThat(OemExtractor.extract("")).isEmpty();
      assertThat(OemExtractor.extract(null)).isEmpty();
   }

   @Test
   void несколькоНомеровТипичнаяДлинаПервой() {
      List<OemExtractor.Candidate> found = OemExtractor.extract("""
            333114 KYB
            48510-06420""");
      assertThat(found).extracting(OemExtractor.Candidate::normalized).containsExactly("4851006420", "333114");
   }
}
