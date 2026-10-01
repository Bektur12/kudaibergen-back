package kg.kudaibergen.admin.users;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kg.kudaibergen.admin.common.AdminNotices;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.sanctions.SanctionTarget;
import kg.kudaibergen.admin.sanctions.Sanctions;
import kg.kudaibergen.admin.search.AdminSearchService;
import kg.kudaibergen.admin.users.AdminUserDtos.AdminUserDetailDto;
import kg.kudaibergen.admin.users.AdminUserDtos.AdminUserRow;
import kg.kudaibergen.admin.users.AdminUserDtos.UserComplaintDto;
import kg.kudaibergen.admin.users.AdminUserDtos.UserCounts;
import kg.kudaibergen.admin.users.AdminUserDtos.UserFilter;
import kg.kudaibergen.admin.users.AdminUserDtos.UserMasterDto;
import kg.kudaibergen.admin.users.AdminUserDtos.UserRequestDto;
import kg.kudaibergen.admin.users.AdminUserDtos.UserShopDto;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Пользователи: список, карточка, блокировка (вместе с боксом и профилем мастера), «Написать». */
@Service
public class AdminUsersService {

   private static final String ROLES = """
         exists (select 1 from shop_members m where m.user_id = u.id) as seller,
         exists (select 1 from masters ms where ms.owner_id = u.id) as master,
         exists (select 1 from admin_members a where a.user_id = u.id and a.is_active) as admin
         """;

   private static final String FILTER = """
          where (cast(:like as text) is null or lower(u.name) like :like escape '\\'
                 or (cast(:digits as text) is not null and u.phone like '%' || :digits || '%'))
         """;

   private final NamedParameterJdbcTemplate jdbc;
   private final UserBlocking blocking;
   private final Sanctions sanctions;
   private final AdminNotices notices;

   public AdminUsersService(NamedParameterJdbcTemplate jdbc, UserBlocking blocking, Sanctions sanctions,
                            AdminNotices notices) {
      this.jdbc = jdbc;
      this.blocking = blocking;
      this.sanctions = sanctions;
      this.notices = notices;
   }

   @Transactional(readOnly = true)
   public AdminPage<AdminUserRow, UserCounts> list(UserFilter filter, String q, String cursor, Integer limit) {
      int size = CursorPage.limit(limit);
      MapSqlParameterSource p = search(q).addValue("after", CursorPage.afterId(cursor)).addValue("limit", size + 1);
      List<AdminUserRow> rows = jdbc.query("select u.id, u.phone, u.name, u.role, u.created_at, u.last_seen_at,"
            + " u.is_blocked, u.blocked_reason, " + ROLES + """
            , (select count(*) from part_requests r where r.buyer_id = u.id)
              + (select count(*) from service_requests s where s.buyer_id = u.id) as requests
              from users u""" + FILTER + " and " + condition(filter) + """
             and (:after = 0 or u.id < :after)
             order by u.id desc limit :limit""", p, (rs, n) -> new AdminUserRow(rs.getLong("id"), rs.getString("phone"),
            rs.getString("name"), roles(rs), rs.getString("role"), rs.getTimestamp("created_at").toInstant(),
            instant(rs, "last_seen_at"), rs.getLong("requests"), rs.getBoolean("is_blocked"),
            rs.getString("blocked_reason"), rs.getBoolean("admin")));
      boolean more = rows.size() > size;
      List<AdminUserRow> page = more ? rows.subList(0, size) : rows;
      UserCounts counts = jdbc.queryForObject("""
            select count(*) as all_count,
                   count(*) filter (where """ + condition(UserFilter.BUYER) + """
                   ) as buyers,
                   count(*) filter (where """ + condition(UserFilter.SELLER) + """
                   ) as sellers,
                   count(*) filter (where """ + condition(UserFilter.MASTER) + """
                   ) as masters,
                   count(*) filter (where u.is_blocked) as blocked
              from users u""" + FILTER, p, (rs, n) -> new UserCounts(rs.getLong("all_count"), rs.getLong("buyers"),
            rs.getLong("sellers"), rs.getLong("masters"), rs.getLong("blocked")));
      return new AdminPage<>(page, more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1).id())) : null,
            counts);
   }

   @Transactional(readOnly = true)
   public AdminUserDetailDto detail(Long userId) {
      Map<String, Object> id = Map.of("id", userId);
      return jdbc.query("select u.*, " + ROLES + ", a.admin_role from users u"
                  + " left join admin_members a on a.user_id = u.id and a.is_active where u.id = :id", id,
            (rs, n) -> new AdminUserDetailDto(userId, rs.getString("phone"), rs.getString("name"), rs.getString("lang"),
                  rs.getString("role"), roles(rs), rs.getTimestamp("created_at").toInstant(),
                  instant(rs, "last_seen_at"), rs.getBoolean("is_blocked"), instant(rs, "blocked_at"),
                  rs.getString("blocked_reason"), rs.getString("admin_role"), cars(userId), shop(userId),
                  master(userId), recentRequests(userId), requestsTotal(userId), complaintsBy(userId),
                  complaintsAbout(userId), sanctions.history(SanctionTarget.USER, userId)))
            .stream().findFirst()
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Пользователь не найден"));
   }

   @Transactional
   public void block(Long userId, Long adminId, String reason) {
      blocking.block(userId, adminId, reason.strip());
   }

   @Transactional
   public void unblock(Long userId, Long adminId) {
      blocking.unblock(userId, adminId);
   }

   @Transactional(readOnly = true)
   public void message(Long userId, String text) {
      detail(userId);
      notices.message(List.of(userId), text.strip());
   }

   // ─────────────────────── внутреннее ───────────────────────

   /** Условие фильтра — с пробелами по краям: склеивается с текстовыми блоками SQL. */
   static String condition(UserFilter filter) {
      return " " + rawCondition(filter) + " ";
   }

   private static String rawCondition(UserFilter filter) {
      return switch (filter) {
         case ALL -> "true";
         case SELLER -> "exists (select 1 from shop_members m where m.user_id = u.id)";
         case MASTER -> "exists (select 1 from masters ms where ms.owner_id = u.id)";
         case BUYER -> "not exists (select 1 from shop_members m where m.user_id = u.id)"
               + " and not exists (select 1 from masters ms where ms.owner_id = u.id)";
         case BLOCKED -> "u.is_blocked";
      };
   }

   private List<String> cars(Long userId) {
      return jdbc.queryForList("""
            select b.name || ' ' || m.name || coalesce(' ' || m.generation, '') || ' · ' || c.year
              from cars c join brands b on b.id = c.brand_id join models m on m.id = c.model_id
             where c.user_id = :id order by c.is_primary desc, c.id""", Map.of("id", userId), String.class);
   }

   private UserShopDto shop(Long userId) {
      return jdbc.query("""
            select s.id, s.name, s.status, m.role from shop_members m join shops s on s.id = m.shop_id
            where m.user_id = :id""", Map.of("id", userId), (rs, n) -> new UserShopDto(rs.getLong("id"),
            rs.getString("name"), rs.getString("status"), rs.getString("role"))).stream().findFirst().orElse(null);
   }

   private UserMasterDto master(Long userId) {
      return jdbc.query("select id, name, status from masters where owner_id = :id", Map.of("id", userId),
            (rs, n) -> new UserMasterDto(rs.getLong("id"), rs.getString("name"), rs.getString("status"))).stream()
            .findFirst().orElse(null);
   }

   private List<UserRequestDto> recentRequests(Long userId) {
      return jdbc.query("""
            (select 'PART' as kind, id, text, status, created_at from part_requests where buyer_id = :id
              order by created_at desc limit 5)
            union all
            (select 'SERVICE', id, description, status, created_at from service_requests where buyer_id = :id
              order by created_at desc limit 5)
            order by created_at desc limit 8""", Map.of("id", userId), (rs, n) -> new UserRequestDto(
            rs.getString("kind"), rs.getLong("id"), rs.getString("text"), rs.getString("status"),
            rs.getTimestamp("created_at").toInstant()));
   }

   private long requestsTotal(Long userId) {
      Long total = jdbc.queryForObject("""
            select (select count(*) from part_requests where buyer_id = :id)
                 + (select count(*) from service_requests where buyer_id = :id)""", Map.of("id", userId), Long.class);
      return total == null ? 0 : total;
   }

   private List<UserComplaintDto> complaintsBy(Long userId) {
      return complaints("select * from complaints where author_id = :id order by created_at desc limit 10", userId);
   }

   /** Жалобы на то, что написал пользователь: его отзывы и сообщения. */
   private List<UserComplaintDto> complaintsAbout(Long userId) {
      return complaints("""
            select * from complaints c where
               (c.type = 'REVIEW' and c.target_id in (select id from reviews where buyer_id = :id))
               or (c.type = 'MASTER_REVIEW' and c.target_id in (select id from master_reviews where buyer_id = :id))
               or (c.type = 'CHAT_MESSAGE' and c.target_id in (select id from messages where sender_id = :id))
            order by c.created_at desc limit 10""", userId);
   }

   private List<UserComplaintDto> complaints(String sql, Long userId) {
      return jdbc.query(sql, Map.of("id", userId), (rs, n) -> new UserComplaintDto(rs.getLong("id"),
            rs.getString("type"), rs.getString("reason"), rs.getString("status"),
            rs.getTimestamp("created_at").toInstant()));
   }

   private static List<String> roles(ResultSet rs) throws SQLException {
      List<String> roles = new ArrayList<>(List.of("BUYER"));
      if (rs.getBoolean("seller")) {
         roles.add("SELLER");
      }
      if (rs.getBoolean("master")) {
         roles.add("MASTER");
      }
      return roles;
   }

   private static MapSqlParameterSource search(String q) {
      String query = q == null || q.isBlank() ? null : q.strip();
      return new MapSqlParameterSource()
            .addValue("like", query == null ? null : "%" + query.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                  .replace("%", "\\%").replace("_", "\\_") + "%", Types.VARCHAR)
            .addValue("digits", query == null ? null : AdminSearchService.phoneDigits(query), Types.VARCHAR);
   }

   private static Instant instant(ResultSet rs, String column) throws SQLException {
      var value = rs.getTimestamp(column);
      return value == null ? null : value.toInstant();
   }
}
