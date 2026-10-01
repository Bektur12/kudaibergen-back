package kg.kudaibergen.admin.shops;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import kg.kudaibergen.admin.search.AdminSearchService;
import kg.kudaibergen.admin.search.AdminSearchService.ContainerQuery;
import kg.kudaibergen.admin.shops.AdminShopDtos.ShopTabCounts;
import kg.kudaibergen.shop.dispute.DisputeStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** SQL списков раздела «Продавцы»: фильтры табов, поиск, счётчики — одним местом. */
@Component
public class AdminShopQueries {

   /** Магазин + владелец + контейнер, который проверяется (новое место при переезде, иначе текущее). */
   private static final String FROM = """
          from shops s
          join users u on u.id = s.owner_id
          join containers c on c.id = coalesce(s.pending_container_id, s.container_id)
         where (cast(:q as text) is null
                or lower(s.name) like :like escape '\\'
                or (cast(:digits as text) is not null and u.phone like '%' || :digits || '%')
                or (cast(:row as text) is not null and exists (
                      select 1 from containers k join market_rows r on r.id = k.row_id
                      where k.id in (s.container_id, s.pending_container_id) and k.number = :number
                        and (lower(r.code) = :row or lower(r.label) = 'ряд ' || :row))))
         """;

   private static final String PENDING = "(s.status = 'PENDING_VERIFICATION' or s.pending_container_id is not null)";
   private static final String DISPUTES = """
         exists (select 1 from container_disputes d
                 where d.status = 'OPEN' and (d.current_shop_id = s.id or d.claimant_shop_id = s.id))""";

   private final NamedParameterJdbcTemplate jdbc;

   public AdminShopQueries(NamedParameterJdbcTemplate jdbc) {
      this.jdbc = jdbc;
   }

   /** Строка списка из базы: что нужно для AdminShopRow, без вычислений над картой. */
   public record ShopRowData(Long id, String name, Long avatarMediaId, String status, String ownerPhone,
                             Long containerId, Long pendingContainerId, boolean tenantPhoneKnown,
                             boolean tenantIsMember, boolean smsConfirmed, int openDisputes, Instant submittedAt,
                             Instant createdAt) {
   }

   public List<ShopRowData> page(ShopTab tab, String q, long afterId, int limit) {
      MapSqlParameterSource params = search(q).addValue("after", afterId).addValue("limit", limit + 1);
      return jdbc.query("""
            select s.id, s.name, s.avatar_media_id, s.status, u.phone as owner_phone, s.container_id,
                   s.pending_container_id, c.tenant_phone is not null as tenant_known,
                   (c.tenant_phone is not null and exists (
                      select 1 from shop_members m join users mu on mu.id = m.user_id
                      where m.shop_id = s.id and mu.phone = c.tenant_phone)) as tenant_member,
                   exists (select 1 from shop_verifications v where v.shop_id = s.id and v.container_id = c.id
                           and v.status = 'APPROVED' and v.method = 'SMS') as sms_ok,
                   (select count(*) from container_disputes d where d.status = 'OPEN'
                      and (d.current_shop_id = s.id or d.claimant_shop_id = s.id)) as disputes,
                   coalesce((select max(v.created_at) from shop_verifications v
                             where v.shop_id = s.id and v.status = 'PENDING'), s.created_at) as submitted_at,
                   s.created_at
            """ + FROM + " and " + tabFilter(tab) + """
             and (:after = 0 or s.id < :after)
            order by s.id desc
            limit :limit""", params, AdminShopQueries::row);
   }

   public ShopTabCounts counts(String q) {
      return jdbc.queryForObject("select count(*) as all_count, count(*) filter (where " + PENDING + ") as pending, "
                  + "count(*) filter (where " + DISPUTES + ") as disputes, "
                  + "count(*) filter (where s.status = 'BLOCKED') as blocked, "
                  + "count(*) filter (where s.status = 'REJECTED') as rejected " + FROM,
            search(q), (rs, n) -> new ShopTabCounts(rs.getLong("all_count"), rs.getLong("pending"),
                  rs.getLong("disputes"), rs.getLong("blocked"), rs.getLong("rejected")));
   }

   /** Сверка с арендатором для одного магазина — тот же расчёт, что в списке. */
   public ShopRowData one(Long shopId) {
      List<ShopRowData> rows = jdbc.query("""
            select s.id, s.name, s.avatar_media_id, s.status, u.phone as owner_phone, s.container_id,
                   s.pending_container_id, c.tenant_phone is not null as tenant_known,
                   (c.tenant_phone is not null and exists (
                      select 1 from shop_members m join users mu on mu.id = m.user_id
                      where m.shop_id = s.id and mu.phone = c.tenant_phone)) as tenant_member,
                   exists (select 1 from shop_verifications v where v.shop_id = s.id and v.container_id = c.id
                           and v.status = 'APPROVED' and v.method = 'SMS') as sms_ok,
                   (select count(*) from container_disputes d where d.status = 'OPEN'
                      and (d.current_shop_id = s.id or d.claimant_shop_id = s.id)) as disputes,
                   coalesce((select max(v.created_at) from shop_verifications v
                             where v.shop_id = s.id and v.status = 'PENDING'), s.created_at) as submitted_at,
                   s.created_at
              from shops s
              join users u on u.id = s.owner_id
              join containers c on c.id = coalesce(s.pending_container_id, s.container_id)
             where s.id = :id""", new MapSqlParameterSource("id", shopId), AdminShopQueries::row);
      return rows.isEmpty() ? null : rows.get(0);
   }

   public List<String> brandNames(Long shopId) {
      return jdbc.queryForList("""
            select b.name from shop_brands sb join brands b on b.id = sb.brand_id
            where sb.shop_id = :id order by b.name""", new MapSqlParameterSource("id", shopId), String.class);
   }

   public List<String> categoryNames(Long shopId) {
      return jdbc.queryForList("""
            select c.name_ru from shop_categories sc join categories c on c.id = sc.category_id
            where sc.shop_id = :id order by c.sort_order""", new MapSqlParameterSource("id", shopId), String.class);
   }

   public long activeParts(Long shopId) {
      return count("select count(*) from parts where shop_id = :id and status = 'ACTIVE'", shopId);
   }

   public long members(Long shopId) {
      return count("select count(*) from shop_members where shop_id = :id", shopId);
   }

   public List<Long> memberIds(Long shopId) {
      return jdbc.queryForList("select user_id from shop_members where shop_id = :id",
            new MapSqlParameterSource("id", shopId), Long.class);
   }

   /** Жалобы на магазин и его товары за 90 дней. */
   public long complaints90d(Long shopId) {
      return count("""
            select count(*) from complaints
            where created_at > now() - interval '90 days'
              and ((type = 'SHOP' and target_id = :id)
                   or (type = 'PART' and target_id in (select p.id from parts p where p.shop_id = :id)))""", shopId);
   }

   public long openDisputes() {
      return jdbc.queryForObject("select count(*) from container_disputes where status = 'OPEN'",
            new MapSqlParameterSource(), Long.class);
   }

   /** Строки споров: статус, участие магазина (null — все), курсор по id. */
   public record DisputeRowData(Long id, String status, Long containerId, Long claimantUserId, String claimantName,
                                String claimantPhone, Long claimantShopId, String claimantShopName,
                                Long currentShopId, String currentShopName, Long currentUserId,
                                String currentUserName, String currentPhone, String tenantName, String tenantPhone,
                                String text, String winner, String resolution, Instant createdAt,
                                Instant resolvedAt) {
   }

   public List<DisputeRowData> disputes(DisputeStatus status, Long shopId, Long disputeId, long afterId, int limit) {
      MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("status", status == null ? null : status.name(), Types.VARCHAR)
            .addValue("shopId", shopId, Types.BIGINT)
            .addValue("disputeId", disputeId, Types.BIGINT)
            .addValue("after", afterId)
            .addValue("limit", limit + 1);
      return jdbc.query("""
            select d.id, d.status, d.container_id, d.claimant_user_id, cu.name as claimant_name,
                   cu.phone as claimant_phone, d.claimant_shop_id, cs.name as claimant_shop_name,
                   d.current_shop_id, ss.name as current_shop_name, su.id as current_user_id,
                   su.name as current_user_name, su.phone as current_phone, c.tenant_name, c.tenant_phone,
                   d.text, d.winner, d.resolution, d.created_at, d.resolved_at
              from container_disputes d
              join containers c on c.id = d.container_id
              left join users cu on cu.id = d.claimant_user_id
              left join shops cs on cs.id = d.claimant_shop_id
              left join shops ss on ss.id = d.current_shop_id
              left join users su on su.id = ss.owner_id
             where (cast(:status as text) is null or d.status = :status)
               and (cast(:shopId as bigint) is null or d.current_shop_id = :shopId or d.claimant_shop_id = :shopId)
               and (cast(:disputeId as bigint) is null or d.id = :disputeId)
               and (:after = 0 or d.id < :after)
             order by d.id desc
             limit :limit""", params, (rs, n) -> new DisputeRowData(rs.getLong("id"), rs.getString("status"),
            rs.getLong("container_id"), (Long) rs.getObject("claimant_user_id"), rs.getString("claimant_name"),
            rs.getString("claimant_phone"), (Long) rs.getObject("claimant_shop_id"),
            rs.getString("claimant_shop_name"), (Long) rs.getObject("current_shop_id"),
            rs.getString("current_shop_name"), (Long) rs.getObject("current_user_id"),
            rs.getString("current_user_name"), rs.getString("current_phone"), rs.getString("tenant_name"),
            rs.getString("tenant_phone"), rs.getString("text"), rs.getString("winner"), rs.getString("resolution"),
            rs.getTimestamp("created_at").toInstant(), instant(rs, "resolved_at")));
   }

   public AdminShopDtos.DisputeCounts disputeCounts() {
      return jdbc.queryForObject("""
            select count(*) filter (where status = 'OPEN') as open, count(*) filter (where status = 'RESOLVED') as resolved
            from container_disputes""", new MapSqlParameterSource(),
            (rs, n) -> new AdminShopDtos.DisputeCounts(rs.getLong("open"), rs.getLong("resolved")));
   }

   private long count(String sql, Long id) {
      Long value = jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), Long.class);
      return value == null ? 0 : value;
   }

   static String tabFilter(ShopTab tab) {
      return switch (tab) {
         case ALL -> "true";
         case PENDING -> PENDING;
         case DISPUTES -> DISPUTES;
         case BLOCKED -> "s.status = 'BLOCKED'";
         case REJECTED -> "s.status = 'REJECTED'";
      };
   }

   /** Поиск: название, цифры телефона владельца или «14 12» (ряд + номер). Пусто — без фильтра. */
   static MapSqlParameterSource search(String q) {
      String query = q == null || q.isBlank() ? null : q.strip();
      ContainerQuery container = query == null ? null : AdminSearchService.parseContainer(query);
      return new MapSqlParameterSource()
            .addValue("q", query, Types.VARCHAR)
            .addValue("like", query == null ? null : "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%",
                  Types.VARCHAR)
            .addValue("digits", query == null ? null : AdminSearchService.phoneDigits(query), Types.VARCHAR)
            .addValue("row", container == null ? null : container.row(), Types.VARCHAR)
            .addValue("number", container == null ? null : container.number(), Types.INTEGER);
   }

   static String escapeLike(String value) {
      return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
   }

   private static ShopRowData row(ResultSet rs, int n) throws SQLException {
      return new ShopRowData(rs.getLong("id"), rs.getString("name"), (Long) rs.getObject("avatar_media_id"),
            rs.getString("status"), rs.getString("owner_phone"), rs.getLong("container_id"),
            (Long) rs.getObject("pending_container_id"), rs.getBoolean("tenant_known"),
            rs.getBoolean("tenant_member"), rs.getBoolean("sms_ok"), rs.getInt("disputes"),
            rs.getTimestamp("submitted_at").toInstant(), rs.getTimestamp("created_at").toInstant());
   }

   private static Instant instant(ResultSet rs, String column) throws SQLException {
      var value = rs.getTimestamp(column);
      return value == null ? null : value.toInstant();
   }
}
