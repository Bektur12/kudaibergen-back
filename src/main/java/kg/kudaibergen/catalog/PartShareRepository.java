package kg.kudaibergen.catalog;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Счётчик «Поделиться» (parts.shares_count): цифра под самолётиком в ленте видео. */
@Repository
public class PartShareRepository {

   private final NamedParameterJdbcTemplate jdbc;

   public PartShareRepository(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   /** +1 опубликованному товару; false — такого нет. */
   public boolean increment(Long partId) {
      return jdbc.update("update parts set shares_count = shares_count + 1 where id = :id and status = 'ACTIVE'",
            Map.of("id", partId)) > 0;
   }

   public Map<Long, Integer> counts(Collection<Long> partIds) {
      Map<Long, Integer> result = new HashMap<>();
      if (partIds.isEmpty()) {
         return result;
      }
      jdbc.query("select id, shares_count from parts where id in (:ids)", Map.of("ids", partIds),
            rs -> {
               result.put(rs.getLong("id"), rs.getInt("shares_count"));
            });
      return result;
   }
}
