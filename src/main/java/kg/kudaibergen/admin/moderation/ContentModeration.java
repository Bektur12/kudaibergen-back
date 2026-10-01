package kg.kudaibergen.admin.moderation;

import java.util.Map;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.complaint.ComplaintType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Скрытие контента администрацией. Флаг hidden_by_admin пишется только здесь (в JPA он только для чтения):
 * запчасть уходит в архив и не публикуется снова, отзыв выпадает из списка и рейтинга, сообщение показывается
 * заглушкой, отклик мастера пропадает у клиента, фото места убирается из профиля.
 */
@Component
public class ContentModeration {

   private final NamedParameterJdbcTemplate jdbc;

   public ContentModeration(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   /** Скрыть объект жалобы. Магазин, мастер и чат целиком не скрываются — для них блокировка. */
   @Transactional
   public void hide(ComplaintType type, Long id, String reason) {
      MapSqlParameterSource params = new MapSqlParameterSource("id", id).addValue("reason", trim(reason));
      int changed = switch (type) {
         case PART -> jdbc.update("""
               update parts set hidden_by_admin = true, hidden_reason = :reason,
                      status = case when status = 'ACTIVE' then 'ARCHIVED' else status end, updated_at = now()
               where id = :id""", params);
         case REVIEW -> hideReview("reviews", "shops", "shop_id", params);
         case MASTER_REVIEW -> hideReview("master_reviews", "masters", "master_id", params);
         case CHAT_MESSAGE -> jdbc.update(
               "update messages set hidden_by_admin = true, hidden_reason = :reason where id = :id", params);
         case SERVICE_OFFER -> jdbc.update(
               "update service_offers set hidden_by_admin = true, hidden_reason = :reason where id = :id", params);
         case SHOP_PHOTO -> removeShopPhoto(params);
         default -> throw new BadRequestException("NOTHING_TO_REMOVE",
               "Это нельзя скрыть — предупредите или заблокируйте");
      };
      if (changed == 0) {
         throw new NotFoundException("CONTENT_NOT_FOUND", "Объект жалобы уже удалён");
      }
   }

   /** Скрыть запрос на запчасть или заявку на услугу [A8]: закрывается и пропадает из лент продавцов / мастеров. */
   @Transactional
   public void hideRequest(boolean service, Long id, String reason) {
      String table = service ? "service_requests" : "part_requests";
      int changed = jdbc.update("update " + table + """
             set hidden_by_admin = true, hidden_reason = :reason,
                 status = case when status = 'ACTIVE' then 'CLOSED' else status end,
                 closed_at = coalesce(closed_at, now())
            where id = :id and not hidden_by_admin""",
            new MapSqlParameterSource("id", id).addValue("reason", trim(reason)));
      if (changed == 0) {
         throw new NotFoundException("REQUEST_NOT_FOUND", "Запрос не найден или уже скрыт");
      }
   }

   private int hideReview(String table, String owners, String ownerColumn, MapSqlParameterSource params) {
      int changed = jdbc.update("update " + table + " set hidden_by_admin = true, hidden_reason = :reason where id = :id",
            params);
      // рейтинг — только по видимым отзывам
      jdbc.update("update " + owners + " o set rating = coalesce((select round(avg(r.stars)::numeric, 1) from " + table
            + " r where r." + ownerColumn + " = o.id and not r.hidden_by_admin), 0), reviews_count = (select count(*) from "
            + table + " r where r." + ownerColumn + " = o.id and not r.hidden_by_admin) where o.id = (select "
            + ownerColumn + " from " + table + " where id = :id)", params);
      return changed;
   }

   private int removeShopPhoto(MapSqlParameterSource params) {
      java.util.List<Long> shops = jdbc.queryForList("select shop_id from shop_photos where media_id = :id", params,
            Long.class);
      int removed = jdbc.update("delete from shop_photos where media_id = :id", params);
      // порядок фото без дыр (первое — обложка); по возрастанию, чтобы новое место всегда было свободно
      for (Long shopId : shops) {
         java.util.List<Integer> sorts = jdbc.queryForList("select sort from shop_photos where shop_id = :shop order by sort",
               Map.of("shop", shopId), Integer.class);
         for (int i = 0; i < sorts.size(); i++) {
            if (sorts.get(i) != i) {
               jdbc.update("update shop_photos set sort = :to where shop_id = :shop and sort = :from",
                     Map.of("to", i, "shop", shopId, "from", sorts.get(i)));
            }
         }
      }
      removed += jdbc.update("update shops set avatar_media_id = null where avatar_media_id = :id", params);
      return removed;
   }

   private static String trim(String reason) {
      if (reason == null || reason.isBlank()) {
         return "Нарушение правил площадки";
      }
      String value = reason.strip();
      return value.length() <= 300 ? value : value.substring(0, 300);
   }
}
