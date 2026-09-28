package kg.kudaibergen.catalog;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Избранные запчасти (сердечко на карточке): связи пользователь–товар без своей сущности. */
@Repository
public class FavoritePartRepository {

   private final NamedParameterJdbcTemplate jdbc;

   public FavoritePartRepository(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   public void add(Long userId, Long partId) {
      jdbc.update("insert into favorite_parts (user_id, part_id) values (:userId, :partId) on conflict do nothing",
            Map.of("userId", userId, "partId", partId));
   }

   public void remove(Long userId, Long partId) {
      jdbc.update("delete from favorite_parts where user_id = :userId and part_id = :partId",
            Map.of("userId", userId, "partId", partId));
   }

   public Set<Long> favoriteAmong(Long userId, Collection<Long> partIds) {
      if (userId == null || partIds.isEmpty()) {
         return Set.of();
      }
      return new HashSet<>(jdbc.queryForList(
            "select part_id from favorite_parts where user_id = :userId and part_id in (:partIds)",
            Map.of("userId", userId, "partIds", partIds), Long.class));
   }

   /** Новые сверху, не больше 200. */
   public List<Long> favoritesOf(Long userId) {
      return jdbc.queryForList(
            "select part_id from favorite_parts where user_id = :userId order by created_at desc limit 200",
            Map.of("userId", userId), Long.class);
   }
}
