package kg.kudaibergen.media.storage;

import java.nio.file.Path;
import java.time.Duration;

import kg.kudaibergen.common.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Dev: фото и вложения чата из локальной папки по /media/**. Имена — UUID, содержимое не меняется. */
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
            .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable());
   }
}
