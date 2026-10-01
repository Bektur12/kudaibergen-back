package kg.kudaibergen.admin.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSearchParsingTest {

   @Test
   void контейнерВРазныхЗаписях() {
      for (String q : new String[]{"14 12", "14-12", "Ряд 14 · 12", "ряд 14/12", "р14 12", "Р.14, 12"}) {
         assertThat(AdminSearchService.parseContainer(q)).as(q)
               .isEqualTo(new AdminSearchService.ContainerQuery("14", 12));
      }
      assertThat(AdminSearchService.parseContainer("Ш 3")).isEqualTo(new AdminSearchService.ContainerQuery("ш", 3));
      assertThat(AdminSearchService.parseContainer("Автозапчасти")).isNull();
      assertThat(AdminSearchService.parseContainer("14")).isNull();
   }

   @Test
   void поискПоТелефонуТолькоДляЦифр() {
      assertThat(AdminSearchService.phoneDigits("+996 555 12")).isEqualTo("99655512");
      assertThat(AdminSearchService.phoneDigits("3456")).isEqualTo("3456");
      assertThat(AdminSearchService.phoneDigits("12")).isNull();
      assertThat(AdminSearchService.phoneDigits("Ряд 14")).isNull();
   }
}
