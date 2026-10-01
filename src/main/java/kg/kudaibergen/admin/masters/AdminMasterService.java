package kg.kudaibergen.admin.masters;

import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.admin.common.AdminNotices;
import kg.kudaibergen.admin.common.AdminNotices.StatusEvent;
import kg.kudaibergen.admin.common.AdminNotices.Target;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.masters.AdminMasterDtos.AdminMasterDetailDto;
import kg.kudaibergen.admin.masters.AdminMasterDtos.AdminMasterOwnerDto;
import kg.kudaibergen.admin.masters.AdminMasterDtos.AdminMasterRow;
import kg.kudaibergen.admin.masters.AdminMasterDtos.AdminMasterStatsDto;
import kg.kudaibergen.admin.masters.AdminMasterDtos.CreateMasterRequest;
import kg.kudaibergen.admin.masters.AdminMasterDtos.MasterTabCounts;
import kg.kudaibergen.admin.sanctions.SanctionTarget;
import kg.kudaibergen.admin.sanctions.SanctionType;
import kg.kudaibergen.admin.sanctions.Sanctions;
import kg.kudaibergen.admin.search.AdminSearchService;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.master.MasterRepository;
import kg.kudaibergen.master.MasterService;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.shop.entity.WeekDays;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.UserService;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Раздел «Мастера» [A7]: список, карточка, решения, «Добавить вручную». */
@Service
public class AdminMasterService {

   private static final String FROM = """
          from masters m
          join users u on u.id = m.owner_id
         where (cast(:q as text) is null or lower(m.name) like :like escape '\\' or lower(m.address) like :like escape '\\'
                or (cast(:digits as text) is not null
                    and (u.phone like '%' || :digits || '%' or m.phone like '%' || :digits || '%')))
           and (cast(:service as text) is null
                or exists (select 1 from master_services ms where ms.master_id = m.id and ms.service = :service))
           and (cast(:brandId as bigint) is null or m.all_brands
                or exists (select 1 from master_brands mb where mb.master_id = m.id and mb.brand_id = :brandId))
         """;

   private final NamedParameterJdbcTemplate jdbc;
   private final MasterRepository masters;
   private final MasterService masterService;
   private final UserRepository users;
   private final UserService userService;
   private final MediaService media;
   private final Sanctions sanctions;
   private final AdminNotices notices;

   public AdminMasterService(NamedParameterJdbcTemplate jdbc, MasterRepository masters, MasterService masterService,
                             UserRepository users, UserService userService, MediaService media, Sanctions sanctions,
                             AdminNotices notices) {
      this.jdbc = jdbc;
      this.masters = masters;
      this.masterService = masterService;
      this.users = users;
      this.userService = userService;
      this.media = media;
      this.sanctions = sanctions;
      this.notices = notices;
   }

   record MasterRowData(Long id, String name, Long avatarMediaId, String status, String ownerPhone, String phone,
                        String address, boolean mobile, int radiusKm, int photos, long openComplaints,
                        java.math.BigDecimal rating, int reviewsCount, Instant createdAt) {
   }

   @Transactional(readOnly = true)
   public AdminPage<AdminMasterRow, MasterTabCounts> list(MasterTab tab, String q, String service, Long brandId,
                                                         String cursor, Integer limit) {
      int size = CursorPage.limit(limit);
      MapSqlParameterSource params = filters(q, service, brandId)
            .addValue("after", CursorPage.afterId(cursor)).addValue("limit", size + 1);
      List<MasterRowData> rows = jdbc.query("""
            select m.id, m.name, m.avatar_media_id, m.status, u.phone as owner_phone, m.phone, m.address,
                   m.is_mobile, m.radius_km, m.rating, m.reviews_count, m.created_at,
                   (select count(*) from master_photos p where p.master_id = m.id) as photos,
                   (select count(*) from complaints c where c.type = 'MASTER' and c.target_id = m.id
                      and c.status = 'OPEN') as open_complaints
            """ + FROM + " and " + tabFilter(tab) + """
             and (:after = 0 or m.id < :after)
            order by m.id desc
            limit :limit""", params, (rs, n) -> new MasterRowData(rs.getLong("id"), rs.getString("name"),
            (Long) rs.getObject("avatar_media_id"), rs.getString("status"), rs.getString("owner_phone"),
            rs.getString("phone"), rs.getString("address"), rs.getBoolean("is_mobile"), rs.getInt("radius_km"),
            rs.getInt("photos"), rs.getLong("open_complaints"), rs.getBigDecimal("rating"),
            rs.getInt("reviews_count"), rs.getTimestamp("created_at").toInstant()));
      boolean more = rows.size() > size;
      List<MasterRowData> page = more ? rows.subList(0, size) : rows;
      List<Long> ids = page.stream().map(MasterRowData::id).toList();
      Set<Long> warned = sanctions.warned(SanctionTarget.MASTER, ids);
      Map<Long, List<String>> services = services(ids);
      Map<Long, String> avatars = media.thumbUrls(page.stream().map(MasterRowData::avatarMediaId).toList());
      List<AdminMasterRow> items = page.stream().map(row -> new AdminMasterRow(row.id(), row.name(),
            row.avatarMediaId() == null ? null : avatars.get(row.avatarMediaId()), ShopStatus.valueOf(row.status()),
            warned.contains(row.id()), row.ownerPhone(), row.phone(), services.getOrDefault(row.id(), List.of()),
            row.address(), row.mobile(), row.radiusKm(), MasterCheck.of(row.photos(), row.openComplaints()),
            row.photos(), row.openComplaints(), row.rating(), row.reviewsCount(), row.createdAt())).toList();
      String next = more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1).id())) : null;
      MasterTabCounts counts = jdbc.queryForObject("""
            select count(*) as all_count,
                   count(*) filter (where m.status = 'PENDING_VERIFICATION') as pending,
                   count(*) filter (where m.is_mobile) as mobile,
                   count(*) filter (where m.status = 'BLOCKED') as blocked,
                   count(*) filter (where m.status = 'REJECTED') as rejected
            """ + FROM, filters(q, service, brandId), (rs, n) -> new MasterTabCounts(rs.getLong("all_count"),
            rs.getLong("pending"), rs.getLong("mobile"), rs.getLong("blocked"), rs.getLong("rejected")));
      return new AdminPage<>(items, next, counts);
   }

   @Transactional(readOnly = true)
   public AdminMasterDetailDto detail(Long masterId) {
      Master master = master(masterId);
      User owner = users.findById(master.getOwnerId()).orElseThrow();
      MapSqlParameterSource id = new MapSqlParameterSource("id", masterId);
      List<String> brands = master.isAllBrands() ? List.of() : jdbc.queryForList("""
            select b.name from master_brands mb join brands b on b.id = mb.brand_id
            where mb.master_id = :id order by b.name""", id, String.class);
      AdminMasterStatsDto stats = jdbc.queryForObject("""
            select (select count(*) from service_recipients r
                     where r.master_id = :id and r.notified_at > now() - interval '30 days') as received,
                   (select count(*) from service_offers o
                     where o.master_id = :id and o.answer = 'CAN_HELP' and o.created_at > now() - interval '30 days') as offers,
                   (select count(*) from service_requests s
                     where s.closed_with_master_id = :id and s.closed_at > now() - interval '30 days') as deals""",
            id, (rs, n) -> new AdminMasterStatsDto(rs.getLong("received"), rs.getLong("offers"), rs.getLong("deals")));
      long openComplaints = count("""
            select count(*) from complaints where type = 'MASTER' and target_id = :id and status = 'OPEN'""", id);
      long complaints90d = count("""
            select count(*) from complaints where type = 'MASTER' and target_id = :id
              and created_at > now() - interval '90 days'""", id);
      Instant since = Instant.now().minus(Duration.ofDays(90));
      return new AdminMasterDetailDto(master.getId(), master.getPublicId(), master.getName(),
            media.thumbUrl(master.getAvatarMediaId()), master.getStatus(), master.getBlockReason(),
            !sanctions.warned(SanctionTarget.MASTER, List.of(masterId)).isEmpty(),
            new AdminMasterOwnerDto(owner.getId(), owner.getName(), owner.getPhone()), master.getPhone(),
            master.getAddress(), master.getLat(), master.getLng(), master.getRadiusKm(), master.isMobile(),
            master.getServices().stream().sorted().toList(), master.isAllBrands(), brands,
            Set.copyOf(master.getOrigins()), master.getOpenFrom(), master.getOpenTo(),
            WeekDays.fromMask(master.getWorkDays()), master.isAccepting(), master.getRating(),
            master.getReviewsCount(), List.copyOf(media.photos(master.getPhotoIds()).values()),
            MasterCheck.of(master.getPhotoIds().size(), openComplaints), stats, openComplaints, complaints90d,
            sanctions.warningsSince(SanctionTarget.MASTER, masterId, since),
            sanctions.history(SanctionTarget.MASTER, masterId), master.getCreatedAt());
   }

   // ─────────────────────── решения ───────────────────────

   @Transactional
   public void approve(Long masterId) {
      Master master = master(masterId);
      if (master.getStatus() != ShopStatus.PENDING_VERIFICATION && master.getStatus() != ShopStatus.REJECTED) {
         throw new ConflictException("NOTHING_TO_VERIFY", "Профиль мастера не ждёт проверки");
      }
      master.approve();
      notices.status(List.of(master.getOwnerId()), Target.MASTER, masterId, StatusEvent.APPROVED, null);
   }

   @Transactional
   public void reject(Long masterId, String reason) {
      Master master = master(masterId);
      if (master.getStatus() == ShopStatus.REJECTED || master.getStatus() == ShopStatus.BLOCKED) {
         throw new ConflictException("NOTHING_TO_VERIFY", "Профиль уже отклонён или заблокирован");
      }
      master.reject(reason);
      notices.status(List.of(master.getOwnerId()), Target.MASTER, masterId, StatusEvent.REJECTED, reason);
   }

   @Transactional
   public void block(Long masterId, Long adminId, String reason) {
      Master master = master(masterId);
      if (master.getStatus() == ShopStatus.BLOCKED) {
         throw new ConflictException("ALREADY_BLOCKED", "Мастер уже заблокирован");
      }
      master.block(reason);
      sanctions.record(SanctionTarget.MASTER, masterId, SanctionType.BLOCK, reason, adminId);
      notices.status(List.of(master.getOwnerId()), Target.MASTER, masterId, StatusEvent.BLOCKED, reason);
   }

   @Transactional
   public void unblock(Long masterId, Long adminId) {
      Master master = master(masterId);
      if (master.getStatus() != ShopStatus.BLOCKED) {
         throw new ConflictException("NOT_BLOCKED", "Мастер не заблокирован");
      }
      master.unblock();
      sanctions.record(SanctionTarget.MASTER, masterId, SanctionType.UNBLOCK, null, adminId);
      notices.status(List.of(master.getOwnerId()), Target.MASTER, masterId, StatusEvent.UNBLOCKED, null);
   }

   @Transactional
   public void warn(Long masterId, Long adminId, String reason) {
      Master master = master(masterId);
      sanctions.record(SanctionTarget.MASTER, masterId, SanctionType.WARNING, reason, adminId);
      notices.warning(List.of(master.getOwnerId()), reason);
   }

   @Transactional(readOnly = true)
   public int message(Long masterId, String text) {
      notices.message(List.of(master(masterId).getOwnerId()), text.strip());
      return 1;
   }

   /** «+ Добавить вручную»: профиль сразу действует, режим приложения пользователя не меняется. */
   @Transactional
   public Long create(CreateMasterRequest request) {
      User user = userService.findOrCreate(request.ownerPhone(), Lang.RU);
      if (request.ownerName() != null && !request.ownerName().isBlank()
            && (user.getName() == null || user.getName().isBlank())) {
         user.setName(request.ownerName().strip());
      }
      return masterService.register(user.getId(), request.profile(), false).getId();
   }

   private Map<Long, List<String>> services(List<Long> ids) {
      if (ids.isEmpty()) {
         return Map.of();
      }
      Map<Long, List<String>> result = new java.util.HashMap<>();
      jdbc.query("select master_id, service from master_services where master_id in (:ids) order by service",
            new MapSqlParameterSource("ids", ids), rs -> {
               result.computeIfAbsent(rs.getLong("master_id"), key -> new java.util.ArrayList<>())
                     .add(rs.getString("service"));
            });
      return result;
   }

   private long count(String sql, MapSqlParameterSource params) {
      Long value = jdbc.queryForObject(sql, params, Long.class);
      return value == null ? 0 : value;
   }

   private Master master(Long masterId) {
      return masters.findById(masterId)
            .orElseThrow(() -> new NotFoundException("MASTER_NOT_FOUND", "Мастер не найден"));
   }

   static String tabFilter(MasterTab tab) {
      return switch (tab) {
         case ALL -> "true";
         case PENDING -> "m.status = 'PENDING_VERIFICATION'";
         case MOBILE -> "m.is_mobile";
         case BLOCKED -> "m.status = 'BLOCKED'";
         case REJECTED -> "m.status = 'REJECTED'";
      };
   }

   static MapSqlParameterSource filters(String q, String service, Long brandId) {
      String query = q == null || q.isBlank() ? null : q.strip();
      return new MapSqlParameterSource()
            .addValue("q", query, Types.VARCHAR)
            .addValue("like", query == null ? null : "%" + query.toLowerCase(Locale.ROOT)
                  .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%", Types.VARCHAR)
            .addValue("digits", query == null ? null : AdminSearchService.phoneDigits(query), Types.VARCHAR)
            .addValue("service", service == null || service.isBlank() ? null : service.strip(), Types.VARCHAR)
            .addValue("brandId", brandId, Types.BIGINT);
   }
}
