package kg.kudaibergen.admin.search;

import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.kudaibergen.admin.search.AdminSearchDto.SearchContainerHit;
import kg.kudaibergen.admin.search.AdminSearchDto.SearchMasterHit;
import kg.kudaibergen.admin.search.AdminSearchDto.SearchShopHit;
import kg.kudaibergen.admin.search.AdminSearchDto.SearchUserHit;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Строка «Телефон, магазин, контейнер…». Телефон — по цифрам (от 3 подряд), имена — по подстроке,
 * контейнер — «14 12», «14-12», «Ряд 14 · 12», «р14/12».
 */
@Service
public class AdminSearchService {

   static final int GROUP_LIMIT = 5;
   static final int MIN_QUERY = 2;

   private static final Pattern CONTAINER = Pattern.compile(
         "^(?:ряд|р\\.?)?\\s*([\\p{L}\\d]+?)\\s*[·\\-/\\s,.:]+\\s*(\\d{1,3})$",
         Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

   private final NamedParameterJdbcTemplate jdbc;

   public AdminSearchService(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   public AdminSearchDto search(String query, AuthPrincipal viewer) {
      String q = query == null ? "" : query.strip();
      if (q.length() < MIN_QUERY) {
         return new AdminSearchDto(
               viewer.can(AdminPermission.USERS_VIEW) ? List.of() : null,
               viewer.can(AdminPermission.SELLERS_VIEW) ? List.of() : null,
               viewer.can(AdminPermission.MASTERS_VIEW) ? List.of() : null,
               viewer.can(AdminPermission.MARKET_VIEW) ? List.of() : null);
      }
      MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("like", "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%")
            .addValue("exact", q.toLowerCase(Locale.ROOT))
            .addValue("digits", phoneDigits(q), Types.VARCHAR)
            .addValue("limit", GROUP_LIMIT);
      return new AdminSearchDto(
            viewer.can(AdminPermission.USERS_VIEW) ? users(params) : null,
            viewer.can(AdminPermission.SELLERS_VIEW) ? shops(params) : null,
            viewer.can(AdminPermission.MASTERS_VIEW) ? masters(params) : null,
            viewer.can(AdminPermission.MARKET_VIEW) ? containers(q) : null);
   }

   private List<SearchUserHit> users(MapSqlParameterSource params) {
      return jdbc.query("""
            select id, phone, name, is_blocked, created_at from users
            where (:digits::text is not null and phone like '%' || :digits || '%') or lower(name) like :like escape '\\'
            order by created_at desc limit :limit""", params, (rs, n) -> new SearchUserHit(rs.getLong("id"),
            rs.getString("phone"), rs.getString("name"), rs.getBoolean("is_blocked"),
            rs.getTimestamp("created_at").toInstant()));
   }

   private List<SearchShopHit> shops(MapSqlParameterSource params) {
      return jdbc.query("""
            select s.id, s.name, s.status, r.label || ' · ' || c.number as location, u.phone
            from shops s
            join users u on u.id = s.owner_id
            left join containers c on c.id = s.container_id
            left join market_rows r on r.id = c.row_id
            where lower(s.name) like :like escape '\\' or s.public_id = :exact
               or (:digits::text is not null and u.phone like '%' || :digits || '%')
            order by s.created_at desc limit :limit""", params, (rs, n) -> new SearchShopHit(rs.getLong("id"),
            rs.getString("name"), rs.getString("status"), rs.getString("location"), rs.getString("phone")));
   }

   private List<SearchMasterHit> masters(MapSqlParameterSource params) {
      return jdbc.query("""
            select m.id, m.name, m.status, m.address, u.phone
            from masters m
            join users u on u.id = m.owner_id
            where lower(m.name) like :like escape '\\'
               or (:digits::text is not null and (u.phone like '%' || :digits || '%' or m.phone like '%' || :digits || '%'))
            order by m.created_at desc limit :limit""", params, (rs, n) -> new SearchMasterHit(rs.getLong("id"),
            rs.getString("name"), rs.getString("status"), rs.getString("address"), rs.getString("phone")));
   }

   private List<SearchContainerHit> containers(String q) {
      ContainerQuery parsed = parseContainer(q);
      if (parsed == null) {
         return List.of();
      }
      return jdbc.query("""
            select c.id, c.row_id, r.label || ' · ' || c.number as label, c.side, s.id as shop_id, s.name as shop_name,
                   c.tenant_phone
            from containers c
            join market_rows r on r.id = c.row_id
            left join shops s on s.container_id = c.id
            where c.number = :number and (lower(r.code) = :row or lower(r.label) = 'ряд ' || :row)
            order by r.sort_order, c.side limit :limit""",
            new MapSqlParameterSource().addValue("number", parsed.number()).addValue("row", parsed.row())
                  .addValue("limit", GROUP_LIMIT),
            (rs, n) -> new SearchContainerHit(rs.getLong("id"), rs.getLong("row_id"), rs.getString("label"),
                  rs.getString("side"), (Long) rs.getObject("shop_id"), rs.getString("shop_name"),
                  rs.getString("tenant_phone")));
   }

   /** «Ряд 14 · 12» → (14, 12); не похоже на контейнер — null. */
   public static ContainerQuery parseContainer(String q) {
      Matcher matcher = CONTAINER.matcher(q.strip());
      if (!matcher.matches()) {
         return null;
      }
      return new ContainerQuery(matcher.group(1).toLowerCase(Locale.ROOT), Integer.parseInt(matcher.group(2)));
   }

   /** Цифры номера телефона: от 3 подряд, иначе null (поиск по телефону не нужен). */
   public static String phoneDigits(String q) {
      if (!q.matches("[+\\d\\s()\\-]+")) {
         return null;
      }
      String digits = q.replaceAll("\\D", "");
      return digits.length() >= 3 ? digits : null;
   }

   private static String escapeLike(String value) {
      return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
   }

   public record ContainerQuery(String row, int number) {
   }
}
