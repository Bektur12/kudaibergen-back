package kg.kudaibergen.common.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Отдаёт загруженные медиа-сообщения чата по /media/**. */
@Configuration
@ConditionalOnProperty(name = "app.media.storage", havingValue = "local", matchIfMissing = true)
public class MediaWebConfig implements WebMvcConfigurer {

   private final AppProperties properties;

   public MediaWebConfig(AppProperties properties) {
      this.properties = properties;
   }

   @Override
   public void addResourceHandlers(ResourceHandlerRegistry registry) {
      Path uploadDir = Path.of(properties.media().uploadDir()).toAbsolutePath().normalize();
      registry.addResourceHandler("/media/**")
            .addResourceLocations("file:" + uploadDir + "/")
            // имена файлов — UUID, содержимое не меняется
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable());
   }
}
