package kg.kudaibergen.release;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppReleaseCompareTest {

   @Test
   void версииСравниваютсяПоЧислам() {
      assertThat(AppReleaseService.compare("1.0.10", "1.0.9")).isPositive();
      assertThat(AppReleaseService.compare("1.0.4", "1.0.5")).isNegative();
      assertThat(AppReleaseService.compare("1.0", "1.0.0")).isZero();
      assertThat(AppReleaseService.compare("1.0.5", "0.0.0")).isPositive();
      assertThat(AppReleaseService.compare("1.1.0-beta", "1.1.0")).isZero();
   }
}
