package kg.kudaibergen.common.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpReviewPhonesTest {

   private static AppProperties.Otp otp(String phones, String code) {
      return new AppProperties.Otp(Duration.ofMinutes(2), Duration.ofSeconds(42), 5, Duration.ofMinutes(15), 5, 20,
            Duration.ofHours(1), "secret", false, null, phones, code);
   }

   @Test
   void reviewPhoneMatchesListedNumberOnly() {
      AppProperties.Otp config = otp("+996700000101, +996700000102", "2468");
      assertThat(config.isReview("+996700000102")).isTrue();
      assertThat(config.isReview("+996700000103")).isFalse();
   }

   @Test
   void offWithoutCode() {
      assertThat(otp("+996700000101", "").isReview("+996700000101")).isFalse();
      assertThat(otp(null, "2468").isReview("+996700000101")).isFalse();
   }
}
