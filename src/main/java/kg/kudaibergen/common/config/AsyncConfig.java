package kg.kudaibergen.common.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

   /** Пул для @Async-задач (рассылка пушей и прочая фоновая работа). */
   @Bean(name = "appTaskExecutor")
   public Executor appTaskExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(4);
      executor.setMaxPoolSize(16);
      executor.setQueueCapacity(500);
      executor.setThreadNamePrefix("kdb-async-");
      executor.initialize();
      return executor;
   }
}
