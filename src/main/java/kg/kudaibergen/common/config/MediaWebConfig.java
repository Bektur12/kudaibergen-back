package kg.kudaibergen.common.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Отдаёт загруженные медиа-сообщения чата по /media/**. */
@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

   private final AppProperties properties;

   public MediaWebConfig(AppProperties properties) {
      this.properties = properties;
   }

   @Override
   public void addResourceHandlers(ResourceHandlerRegistry registry) {
      Path uploadDir = Path.of(properties.media().uploadDir()).toAbsolutePath().normalize();
      registry.addResourceHandler("/media/**")
            .addResourceLocations("file:" + uploadDir + "/");
   }
}
