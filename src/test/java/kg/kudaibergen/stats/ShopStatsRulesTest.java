package kg.kudaibergen.stats;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShopStatsRulesTest {

   @Test
   void среднееВремяОтветаВМинутах() {
      assertThat(ShopStatsService.avgMinutes(null)).isNull();
      assertThat(ShopStatsService.avgMinutes(12.0)).isEqualTo(1);
      assertThat(ShopStatsService.avgMinutes(240.0)).isEqualTo(4);
      assertThat(ShopStatsService.avgMinutes(270.0)).isEqualTo(5);
   }

   @Test
   void периодНеделяИМесяц() {
      assertThat(StatsPeriod.WEEK.days()).isEqualTo(7);
      assertThat(StatsPeriod.MONTH.length().toDays()).isEqualTo(30);
   }
}
