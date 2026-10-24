package kg.kudaibergen.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import kg.kudaibergen.garage.entity.FuelType;
import org.junit.jupiter.api.Test;

class TechPassportReaderTest {

   private static final String TEXT = """
         СВИДЕТЕЛЬСТВО О РЕГИСТРАЦИИ ТРАНСПОРТНОГО СРЕДСТВА
         Марка, модель / Маркасы, модели: TOYOTA CAMRY
         Идентификационный номер (VIN): JTNBE40K303123456
         Год выпуска / Чыгарылган жылы: 2012
         Объем двигателя, см3: 2494
         Тип топлива: бензин
         Гос. номер 01KG123ABC""";

   @Test
   void fields() {
      String lower = TEXT.toLowerCase();
      assertThat(TechPassportReader.vin(TEXT)).isEqualTo("JTNBE40K303123456");
      assertThat(TechPassportReader.year(lower)).isEqualTo(2012);
      assertThat(TechPassportReader.volume(lower)).isEqualByComparingTo(new BigDecimal("2.5"));
      assertThat(TechPassportReader.fuel(lower)).isEqualTo(FuelType.PETROL);
   }

   @Test
   void nameAsWholeWord() {
      assertThat(TechPassportReader.firstIndex("TOYOTA CAMRY", java.util.List.of("Camry"))).isEqualTo(7);
      assertThat(TechPassportReader.firstIndex("TOYOTA CAMRYX", java.util.List.of("Camry"))).isEqualTo(-1);
   }
}
