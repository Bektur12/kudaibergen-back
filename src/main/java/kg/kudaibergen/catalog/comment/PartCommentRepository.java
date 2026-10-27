package kg.kudaibergen.catalog.comment;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** Комментарии под видео — без своей сущности: видимые — не удалённые и не скрытые админом. */
@Repository
public class PartCommentRepository {

   private static final String VISIBLE = "c.deleted_at is null and not c.hidden_by_admin";

   private final NamedParameterJdbcTemplate jdbc;

   public PartCommentRepository(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   public record CommentRow(Long id, Long userId, String userName, String text, Instant createdAt) {
   }

   public Long add(Long partId, Long userId, String text) {
      GeneratedKeyHolder key = new GeneratedKeyHolder();
      jdbc.update("insert into part_comments (part_id, user_id, text) values (:partId, :userId, :text)",
            new MapSqlParameterSource("partId", partId).addValue("userId", userId).addValue("text", text), key,
            new String[] {"id"});
      return key.getKeyAs(Long.class);
   }

   /** Новые сверху; beforeId — курсор (id последнего на прошлой странице). */
   public List<CommentRow> page(Long partId, Long beforeId, int limit) {
      MapSqlParameterSource params = new MapSqlParameterSource("partId", partId).addValue("limit", limit)
            .addValue("beforeId", beforeId);
      String sql = "select c.id, c.user_id, u.name, c.text, c.created_at"
            + " from part_comments c join users u on u.id = c.user_id"
            + " where c.part_id = :partId and " + VISIBLE
            + (beforeId == null ? "" : " and c.id < :beforeId")
            + " order by c.id desc limit :limit";
      return jdbc.query(sql, params, (rs, n) -> new CommentRow(rs.getLong("id"), rs.getLong("user_id"), rs.getString("name"),
            rs.getString("text"), rs.getTimestamp("created_at").toInstant()));
   }

   public long count(Long partId) {
      Long total = jdbc.queryForObject("select count(*) from part_comments c where c.part_id = :partId and " + VISIBLE,
            Map.of("partId", partId), Long.class);
      return total == null ? 0 : total;
   }

   /** Сколько комментариев у каждого товара — счётчик в ленте. */
   public Map<Long, Integer> counts(Collection<Long> partIds) {
      Map<Long, Integer> result = new HashMap<>();
      if (partIds.isEmpty()) {
         return result;
      }
      jdbc.query("select c.part_id, count(*) as n from part_comments c where c.part_id in (:ids) and " + VISIBLE
                  + " group by c.part_id", Map.of("ids", partIds),
            rs -> {
               result.put(rs.getLong("part_id"), rs.getInt("n"));
            });
      return result;
   }

   /** Автор видимого комментария этого товара; null — нет такого. */
   public Long authorOf(Long partId, Long commentId) {
      List<Long> ids = jdbc.queryForList("select c.user_id from part_comments c where c.id = :id and c.part_id = :partId and "
            + VISIBLE, Map.of("id", commentId, "partId", partId), Long.class);
      return ids.isEmpty() ? null : ids.get(0);
   }

   public void delete(Long commentId) {
      jdbc.update("update part_comments set deleted_at = now() where id = :id and deleted_at is null",
            Map.of("id", commentId));
   }
}
