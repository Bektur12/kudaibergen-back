package kg.kudaibergen.common.web;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Версия справочников (марки, модели, категории, услуги, подсказки): растёт при любой правке в админке.
 * Клиент сверяет её при запуске и перекачивает справочники; сами списки ещё отдают ETag.
 */
@Component
public class DictionaryVersion {

   private final JdbcTemplate jdbc;

   public DictionaryVersion(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   public record Current(long version, Instant updatedAt) {
   }

   public Current current() {
      return jdbc.queryForObject("select version, updated_at from dictionary_version where id = 1",
            (rs, n) -> new Current(rs.getLong("version"), rs.getTimestamp("updated_at").toInstant()));
   }

   /** В транзакции правки: версия видна клиентам вместе с самой правкой. */
   public void bump() {
      jdbc.update("update dictionary_version set version = version + 1, updated_at = now() where id = 1");
   }
}
