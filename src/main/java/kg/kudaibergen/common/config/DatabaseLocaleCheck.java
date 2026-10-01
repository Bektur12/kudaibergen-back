package kg.kudaibergen.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * База с локалью C не переводит кириллицу в нижний регистр: полнотекстовый поиск («колодки» не находит
 * «Колодки»), поиск магазинов и опечатки перестают работать без единой ошибки. Предупреждаем при старте.
 * Лечится базой в UTF-8: CREATE DATABASE … TEMPLATE template0 ENCODING 'UTF8' LOCALE_PROVIDER icu ICU_LOCALE 'ru-RU' …
 */
@Component
public class DatabaseLocaleCheck {

   private static final Logger log = LoggerFactory.getLogger(DatabaseLocaleCheck.class);

   private final JdbcTemplate jdbc;

   public DatabaseLocaleCheck(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   @EventListener(ApplicationReadyEvent.class)
   public void check() {
      Boolean cyrillicLower = jdbc.queryForObject("select lower('КОЛОДКИ') = 'колодки'", Boolean.class);
      if (!Boolean.TRUE.equals(cyrillicLower)) {
         log.warn("""
               База не переводит кириллицу в нижний регистр (локаль C): поиск по-русски работать не будет. \
               Создайте базу в UTF-8 — см. README, раздел «Локальная база».""");
      }
   }
}
