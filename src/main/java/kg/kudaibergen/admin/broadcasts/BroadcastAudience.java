package kg.kudaibergen.admin.broadcasts;

import java.util.List;

import kg.kudaibergen.admin.broadcasts.BroadcastDtos.Audience;
import kg.kudaibergen.admin.broadcasts.BroadcastDtos.BroadcastFilters;
import kg.kudaibergen.common.error.BadRequestException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

/** SQL аудитории рассылки: id пользователей, которые не заблокированы и не удаляют аккаунт. */
@Component
public class BroadcastAudience {

   /** Подзапрос «id пользователей аудитории» и его параметры. */
   public record AudienceQuery(String sql, MapSqlParameterSource params) {
   }

   public AudienceQuery of(Audience audience, BroadcastFilters filters) {
      BroadcastFilters f = filters == null ? BroadcastFilters.none() : filters;
      List<Long> brands = f.brandIds() == null ? List.of() : f.brandIds();
      List<Long> rows = f.rowIds() == null ? List.of() : f.rowIds();
      List<String> services = f.serviceTypes() == null ? List.of() : f.serviceTypes();
      if (!rows.isEmpty() && audience != Audience.SELLERS) {
         throw new BadRequestException("BAD_FILTER", "Фильтр по рядам — только для продавцов");
      }
      if (!services.isEmpty() && audience != Audience.MASTERS) {
         throw new BadRequestException("BAD_FILTER", "Фильтр по услугам — только для мастеров");
      }
      if (!brands.isEmpty() && audience == Audience.ALL) {
         throw new BadRequestException("BAD_FILTER", "Фильтр по маркам — для покупателей, продавцов или мастеров");
      }
      MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("brands", brands.toArray(Long[]::new))
            .addValue("rows", rows.toArray(Long[]::new))
            .addValue("services", services.toArray(String[]::new));
      String members = switch (audience) {
         case ALL -> "select id from users";
         case BUYERS -> """
               select u2.id from users u2 where u2.role = 'BUYER'
                 and (cardinality(cast(:brands as bigint[])) = 0
                      or exists (select 1 from cars c where c.user_id = u2.id and c.brand_id = any(cast(:brands as bigint[]))))""";
         case SELLERS -> """
               select m.user_id from shop_members m join shops s on s.id = m.shop_id
                where s.status = 'ACTIVE'
                  and (cardinality(cast(:brands as bigint[])) = 0 or exists (select 1 from shop_brands sb
                       where sb.shop_id = s.id and sb.brand_id = any(cast(:brands as bigint[]))))
                  and (cardinality(cast(:rows as bigint[])) = 0 or exists (select 1 from containers c
                       where c.id = s.container_id and c.row_id = any(cast(:rows as bigint[]))))""";
         case MASTERS -> """
               select ms.owner_id from masters ms
                where ms.status = 'ACTIVE'
                  and (cardinality(cast(:services as text[])) = 0 or exists (select 1 from master_services mv
                       where mv.master_id = ms.id and mv.service = any(cast(:services as text[]))))
                  and (cardinality(cast(:brands as bigint[])) = 0 or ms.all_brands or exists (select 1 from master_brands mb
                       where mb.master_id = ms.id and mb.brand_id = any(cast(:brands as bigint[]))))""";
      };
      return new AudienceQuery("""
            select u.id from users u
             where not u.is_blocked and u.deletion_requested_at is null
               and u.id in (""" + members + ")", params);
   }

   /** «Получат 64 продавца». */
   public static String label(Audience audience, long count) {
      String[] forms = switch (audience) {
         case ALL -> new String[]{"пользователь", "пользователя", "пользователей"};
         case BUYERS -> new String[]{"покупатель", "покупателя", "покупателей"};
         case SELLERS -> new String[]{"продавец", "продавца", "продавцов"};
         case MASTERS -> new String[]{"мастер", "мастера", "мастеров"};
      };
      long mod10 = count % 10;
      long mod100 = count % 100;
      String word = mod10 == 1 && mod100 != 11 ? forms[0]
            : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? forms[1] : forms[2];
      return (count == 1 && mod100 != 11 ? "Получит " : "Получат ") + count + " " + word;
   }
}
