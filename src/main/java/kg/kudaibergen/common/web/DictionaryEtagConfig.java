package kg.kudaibergen.common.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * ETag на списках справочников: повторный запрос с If-None-Match получает 304 без тела, пока в админке
 * ничего не поменяли.
 */
@Configuration
public class DictionaryEtagConfig {

   @Bean
   public FilterRegistrationBean<ShallowEtagHeaderFilter> dictionaryEtags() {
      FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
            new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
      registration.addUrlPatterns("/api/v1/brands", "/api/v1/brands/*", "/api/v1/models", "/api/v1/categories",
            "/api/v1/service-types", "/api/v1/requests/hints", "/api/v1/dictionaries/version");
      registration.setName("dictionaryEtags");
      return registration;
   }
}
