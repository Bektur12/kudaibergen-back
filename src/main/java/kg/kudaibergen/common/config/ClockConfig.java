package kg.kudaibergen.common.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Часы в поясе рынка: «открыт до 17:00» считается по Бишкеку, а не по UTC сервера. */
@Configuration
public class ClockConfig {

   public static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Bishkek");

   @Bean
   public Clock clock() {
      return Clock.system(MARKET_ZONE);
   }
}
