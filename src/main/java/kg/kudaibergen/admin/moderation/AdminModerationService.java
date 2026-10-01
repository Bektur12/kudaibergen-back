package kg.kudaibergen.admin.moderation;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.admin.common.AdminNotices;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.masters.AdminMasterService;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ComplaintCounts;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ComplaintDetailDto;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ComplaintRow;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ComplaintSubjectDto;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ModerationAction;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.PartyDto;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.PartyStatsDto;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.RelatedRequestDto;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ReporterDto;
import kg.kudaibergen.admin.moderation.AdminModerationDtos.ResolvedComplaintDto;
import kg.kudaibergen.admin.sanctions.SanctionTarget;
import kg.kudaibergen.admin.sanctions.SanctionType;
import kg.kudaibergen.admin.sanctions.Sanctions;
import kg.kudaibergen.admin.shops.AdminShopService;
import kg.kudaibergen.admin.users.UserBlocking;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.complaint.Complaint;
import kg.kudaibergen.complaint.ComplaintOutcome;
import kg.kudaibergen.complaint.ComplaintRepository;
import kg.kudaibergen.complaint.ComplaintStatus;
import kg.kudaibergen.complaint.ComplaintType;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.PhotoDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Модерация [A4]: лента жалоб, карточка объекта, решение. Решение применяется к объекту, ответственный получает
 * пуш с комментарием, заявитель — что жалоба рассмотрена; остальные открытые жалобы на тот же объект закрываются
 * тем же решением. Каждое действие требует своего права: скрыть — CONTENT_REMOVE, предупредить — SELLER_WARN,
 * заблокировать — SELLERS_BLOCK / MASTERS_BLOCK / USERS_BLOCK.
 */
@Service
public class AdminModerationService {

   private final NamedParameterJdbcTemplate jdbc;
   private final ComplaintRepository complaints;
   private final ContentModeration content;
   private final Sanctions sanctions;
   private final AdminNotices notices;
   private final AdminShopService shops;
   private final AdminMasterService masters;
   private final UserBlocking users;
   private final MediaService media;
   private final MarketMapService market;

   public AdminModerationService(NamedParameterJdbcTemplate jdbc, ComplaintRepository complaints,
                                 ContentModeration content, Sanctions sanctions, AdminNotices notices,
                                 AdminShopService shops, AdminMasterService masters, UserBlocking users,
                                 MediaService media, MarketMapService market) {
      this.jdbc = jdbc;
      this.complaints = complaints;
      this.content = content;
      this.sanctions = sanctions;
      this.notices = notices;
      this.shops = shops;
      this.masters = masters;
      this.users = users;
      this.media = media;
      this.market = market;
   }

   // ─────────────────────── лента ───────────────────────

   @Transactional(readOnly = true)
   public AdminPage<ComplaintRow, ComplaintCounts> list(ComplaintStatus status, ComplaintType type, String cursor,
                                                       Integer limit) {
      int size = CursorPage.limit(limit);
      MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("status", status == null ? null : status.name(), Types.VARCHAR)
            .addValue("type", type == null ? null : type.name(), Types.VARCHAR)
            .addValue("after", CursorPage.afterId(cursor))
            .addValue("limit", size + 1);
      List<Complaint> rows = complaints.findAllById(jdbc.queryForList("""
            select id from complaints
            where type <> 'CONTAINER_CLAIM'
              and (cast(:status as text) is null or status = :status)
              and (cast(:type as text) is null or type = :type)
              and (:after = 0 or id < :after)
            order by id desc limit :limit""", params, Long.class));
      rows = new ArrayList<>(rows);
      rows.sort((a, b) -> Long.compare(b.getId(), a.getId()));
      boolean more = rows.size() > size;
      List<Complaint> page = more ? rows.subList(0, size) : rows;
      List<ComplaintRow> items = page.stream().map(complaint -> {
         Resolved target = resolve(complaint);
         return new ComplaintRow(complaint.getId(), complaint.getType(), complaint.getReason(), complaint.getStatus(),
               complaint.getOutcome(), target.subject == null ? "Объект удалён" : target.subject.title(),
               target.party == null ? null : target.party.name(), reporter(complaint), complaint.getText(),
               complaint.getCreatedAt());
      }).toList();
      ComplaintCounts counts = jdbc.queryForObject("""
            select count(*) filter (where status = 'OPEN') as open, count(*) filter (where status = 'RESOLVED') as resolved,
                   count(*) filter (where status = 'REJECTED') as rejected
            from complaints where type <> 'CONTAINER_CLAIM'
              and (cast(:type as text) is null or type = :type)""", params,
            (rs, n) -> new ComplaintCounts(rs.getLong("open"), rs.getLong("resolved"), rs.getLong("rejected")));
      String next = more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1).getId())) : null;
      return new AdminPage<>(items, next, counts);
   }

   @Transactional(readOnly = true)
   public ComplaintDetailDto detail(Long complaintId) {
      Complaint complaint = complaint(complaintId);
      Resolved target = resolve(complaint);
      Long same = jdbc.queryForObject("""
            select count(*) from complaints where type = :type and target_id = :target and status = 'OPEN' and id <> :id""",
            Map.of("type", complaint.getType().name(), "target", complaint.getTargetId(), "id", complaintId), Long.class);
      return new ComplaintDetailDto(complaint.getId(), complaint.getType(), complaint.getReason(),
            complaint.getStatus(), complaint.getOutcome(), complaint.getText(), complaint.getCreatedAt(),
            reporter(complaint), target.subject, target.party,
            target.party == null ? null : stats(target.party), related(complaint), same == null ? 0 : same,
            complaint.getResolution(), complaint.getResolvedBy(), complaint.getResolvedAt());
   }

   // ─────────────────────── решение ───────────────────────

   @Transactional
   public ResolvedComplaintDto resolve(Long complaintId, ModerationAction action, String comment, AuthPrincipal admin) {
      Complaint complaint = complaint(complaintId);
      if (complaint.getStatus() != ComplaintStatus.OPEN) {
         throw new ConflictException("COMPLAINT_RESOLVED", "Жалоба уже рассмотрена");
      }
      Resolved target = resolve(complaint);
      ComplaintOutcome outcome;
      switch (action) {
         case REMOVE_CONTENT -> {
            require(admin, AdminPermission.CONTENT_REMOVE);
            content.hide(complaint.getType(), complaint.getTargetId(), comment);
            if (target.party != null) {
               notices.contentRemoved(recipients(target.party), comment);
            }
            outcome = ComplaintOutcome.REMOVED;
         }
         case WARN_SELLER -> {
            require(admin, AdminPermission.SELLER_WARN);
            PartyDto party = requireParty(target);
            String reason = comment == null || comment.isBlank() ? "Жалоба покупателя подтвердилась" : comment.strip();
            sanctions.record(party.kind(), party.id(), SanctionType.WARNING, reason, admin.userId());
            notices.warning(recipients(party), reason);
            outcome = ComplaintOutcome.WARNED;
         }
         case BLOCK_SHOP -> {
            PartyDto party = requireParty(target);
            String reason = comment == null || comment.isBlank() ? "Нарушение правил площадки" : comment.strip();
            switch (party.kind()) {
               case SHOP -> {
                  require(admin, AdminPermission.SELLERS_BLOCK);
                  shops.block(party.id(), admin.userId(), shorten(reason));
               }
               case MASTER -> {
                  require(admin, AdminPermission.MASTERS_BLOCK);
                  masters.block(party.id(), admin.userId(), shorten(reason));
               }
               case USER -> {
                  require(admin, AdminPermission.USERS_BLOCK);
                  users.block(party.id(), admin.userId(), shorten(reason));
               }
            }
            outcome = ComplaintOutcome.BLOCKED;
         }
         default -> outcome = ComplaintOutcome.UNFOUNDED;
      }
      ComplaintStatus status = outcome == ComplaintOutcome.UNFOUNDED ? ComplaintStatus.REJECTED : ComplaintStatus.RESOLVED;
      String note = comment == null || comment.isBlank() ? null : comment.strip();
      List<Long> sameIds = jdbc.queryForList("""
            select id from complaints where type = :type and target_id = :target and status = 'OPEN'""",
            Map.of("type", complaint.getType().name(), "target", complaint.getTargetId()), Long.class);
      List<Long> reporters = new ArrayList<>();
      for (Complaint same : complaints.findAllById(sameIds)) {
         same.resolve(status, outcome, note, admin.userId());
         if (same.getAuthorId() != null) {
            reporters.add(same.getAuthorId());
         }
      }
      notices.complaintResolved(reporters, complaintId, outcome != ComplaintOutcome.UNFOUNDED);
      complaints.flush();
      return new ResolvedComplaintDto(detail(complaintId), Math.max(0, sameIds.size() - 1));
   }

   // ─────────────────────── объект и ответственный ───────────────────────

   private record Resolved(ComplaintSubjectDto subject, PartyDto party) {
   }

   private Resolved resolve(Complaint complaint) {
      Long id = complaint.getTargetId();
      Map<String, Object> p = Map.of("id", id);
      return switch (complaint.getType()) {
         case PART -> one("""
               select p.id, p.title, p.price, p.status, p.hidden_by_admin, p.hidden_reason, p.created_at, p.shop_id
               from parts p where p.id = :id""", p, (rs, n) -> new Resolved(new ComplaintSubjectDto(ComplaintType.PART,
               id, nvl(rs.getString("title"), "Запчасть без названия"), null, partPhotos(id), integer(rs, "price"),
               null, fitments(id), rs.getString("status"), rs.getBoolean("hidden_by_admin"),
               rs.getString("hidden_reason"), instant(rs, "created_at")), shopParty(rs.getLong("shop_id"))));
         case REVIEW -> one("""
               select r.id, r.stars, r.tags, r.reply_text, r.hidden_by_admin, r.hidden_reason, r.created_at, r.shop_id,
                      r.buyer_id from reviews r where r.id = :id""", p, (rs, n) -> new Resolved(
               new ComplaintSubjectDto(ComplaintType.REVIEW, id, "Отзыв ★" + rs.getInt("stars"), tags(rs), List.of(),
                     null, rs.getInt("stars"), List.of(), "о магазине: " + nvl(shopName(rs.getLong("shop_id")), "—"),
                     rs.getBoolean("hidden_by_admin"), rs.getString("hidden_reason"), instant(rs, "created_at")),
               userParty((Long) rs.getObject("buyer_id"))));
         case MASTER_REVIEW -> one("""
               select r.id, r.stars, r.tags, r.hidden_by_admin, r.hidden_reason, r.created_at, r.master_id, r.buyer_id
               from master_reviews r where r.id = :id""", p, (rs, n) -> new Resolved(
               new ComplaintSubjectDto(ComplaintType.MASTER_REVIEW, id, "Отзыв о мастере ★" + rs.getInt("stars"),
                     tags(rs), List.of(), null, rs.getInt("stars"), List.of(), null, rs.getBoolean("hidden_by_admin"),
                     rs.getString("hidden_reason"), instant(rs, "created_at")),
               userParty((Long) rs.getObject("buyer_id"))));
         case CHAT_MESSAGE -> one("""
               select m.id, m.text, m.type, m.side, m.sender_id, m.hidden_by_admin, m.hidden_reason, m.created_at,
                      c.shop_id, c.master_id, c.buyer_id
               from messages m join chats c on c.id = m.chat_id where m.id = :id""", p, (rs, n) -> {
            String text = rs.getString("text");
            PartyDto party = "BUYER".equals(rs.getString("side")) ? userParty((Long) rs.getObject("sender_id"))
                  : provider(rs);
            return new Resolved(new ComplaintSubjectDto(ComplaintType.CHAT_MESSAGE, id,
                  text == null ? "Сообщение (" + rs.getString("type") + ")" : shorten(text, 80), text, List.of(), null,
                  null, List.of(), rs.getString("type"), rs.getBoolean("hidden_by_admin"),
                  rs.getString("hidden_reason"), instant(rs, "created_at")), party);
         });
         case CHAT -> one("select c.id, c.shop_id, c.master_id, c.buyer_id, c.created_at from chats c where c.id = :id",
               p, (rs, n) -> {
            Long buyer = rs.getLong("buyer_id");
            PartyDto party = buyer.equals(complaint.getAuthorId()) ? provider(rs) : userParty(buyer);
            return new Resolved(new ComplaintSubjectDto(ComplaintType.CHAT, id, "Чат №" + id, null, List.of(), null,
                  null, List.of(), null, false, null, instant(rs, "created_at")), party);
         });
         case SHOP -> one("select s.id, s.name, s.status, s.created_at from shops s where s.id = :id", p,
               (rs, n) -> new Resolved(new ComplaintSubjectDto(ComplaintType.SHOP, id, rs.getString("name"), null,
                     List.of(), null, null, List.of(), rs.getString("status"), false, null, instant(rs, "created_at")),
                     shopParty(id)));
         case SHOP_PHOTO -> one("select sp.shop_id from shop_photos sp where sp.media_id = :id limit 1", p,
               (rs, n) -> new Resolved(new ComplaintSubjectDto(ComplaintType.SHOP_PHOTO, id, "Фото места", null,
                     List.copyOf(media.photos(List.of(id)).values()), null, null, List.of(), null, false, null, null),
                     shopParty(rs.getLong("shop_id"))));
         case MASTER -> one("select m.id, m.name, m.status, m.address, m.created_at from masters m where m.id = :id", p,
               (rs, n) -> new Resolved(new ComplaintSubjectDto(ComplaintType.MASTER, id, rs.getString("name"),
                     rs.getString("address"), List.of(), null, null, List.of(), rs.getString("status"), false, null,
                     instant(rs, "created_at")), masterParty(id)));
         case SERVICE_OFFER -> one("""
               select o.id, o.message, o.price_from, o.answer, o.hidden_by_admin, o.hidden_reason, o.created_at, o.master_id
               from service_offers o where o.id = :id""", p, (rs, n) -> new Resolved(
               new ComplaintSubjectDto(ComplaintType.SERVICE_OFFER, id, "Отклик мастера", rs.getString("message"),
                     List.of(), integer(rs, "price_from"), null, List.of(), rs.getString("answer"),
                     rs.getBoolean("hidden_by_admin"), rs.getString("hidden_reason"), instant(rs, "created_at")),
               masterParty(rs.getLong("master_id"))));
         case CONTAINER_CLAIM -> new Resolved(null, null);
      };
   }

   private Resolved one(String sql, Map<String, Object> params,
                        org.springframework.jdbc.core.RowMapper<Resolved> mapper) {
      return jdbc.query(sql, params, mapper).stream().findFirst().orElse(new Resolved(null, null));
   }

   private PartyDto provider(ResultSet rs) throws SQLException {
      Object shop = rs.getObject("shop_id");
      return shop != null ? shopParty(((Number) shop).longValue()) : masterParty(rs.getLong("master_id"));
   }

   private PartyDto shopParty(Long shopId) {
      return jdbc.query("""
            select s.id, s.name, s.status, s.container_id, u.phone from shops s join users u on u.id = s.owner_id
            where s.id = :id""", Map.of("id", shopId), (rs, n) -> {
         LocationDto location = market.location(rs.getLong("container_id"));
         return new PartyDto(SanctionTarget.SHOP, rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
               rs.getString("status"), location.rowLabel() + " · " + location.number());
      }).stream().findFirst().orElse(null);
   }

   private PartyDto masterParty(Long masterId) {
      return jdbc.query("""
            select m.id, m.name, m.status, m.address, u.phone from masters m join users u on u.id = m.owner_id
            where m.id = :id""", Map.of("id", masterId), (rs, n) -> new PartyDto(SanctionTarget.MASTER, rs.getLong("id"),
            rs.getString("name"), rs.getString("phone"), rs.getString("status"), rs.getString("address")))
            .stream().findFirst().orElse(null);
   }

   private PartyDto userParty(Long userId) {
      if (userId == null) {
         return null;
      }
      return jdbc.query("select id, name, phone, is_blocked from users where id = :id", Map.of("id", userId),
            (rs, n) -> new PartyDto(SanctionTarget.USER, rs.getLong("id"), nvl(rs.getString("name"), "Пользователь"),
                  rs.getString("phone"), rs.getBoolean("is_blocked") ? "BLOCKED" : "ACTIVE", null))
            .stream().findFirst().orElse(null);
   }

   private List<Long> recipients(PartyDto party) {
      return switch (party.kind()) {
         case SHOP -> jdbc.queryForList("select user_id from shop_members where shop_id = :id",
               Map.of("id", party.id()), Long.class);
         case MASTER -> jdbc.queryForList("select owner_id from masters where id = :id", Map.of("id", party.id()),
               Long.class);
         case USER -> List.of(party.id());
      };
   }

   private PartyStatsDto stats(PartyDto party) {
      String complaintsSql = switch (party.kind()) {
         case SHOP -> """
               select count(*) from complaints where created_at > now() - interval '90 days' and (
                  (type = 'SHOP' and target_id = :id)
                  or (type = 'PART' and target_id in (select id from parts where shop_id = :id))
                  or (type = 'SHOP_PHOTO' and target_id in (select media_id from shop_photos where shop_id = :id)))""";
         case MASTER -> """
               select count(*) from complaints where created_at > now() - interval '90 days' and (
                  (type = 'MASTER' and target_id = :id)
                  or (type = 'SERVICE_OFFER' and target_id in (select id from service_offers where master_id = :id)))""";
         case USER -> """
               select count(*) from complaints where created_at > now() - interval '90 days' and (
                  (type = 'REVIEW' and target_id in (select id from reviews where buyer_id = :id))
                  or (type = 'MASTER_REVIEW' and target_id in (select id from master_reviews where buyer_id = :id))
                  or (type = 'CHAT_MESSAGE' and target_id in (select id from messages where sender_id = :id)))""";
      };
      Map<String, Object> id = Map.of("id", party.id());
      Long complaints90d = jdbc.queryForObject(complaintsSql, id, Long.class);
      Long removed = jdbc.queryForObject(complaintsSql.replace("select count(*) from complaints where",
            "select count(*) from complaints where outcome = 'REMOVED' and"), id, Long.class);
      long warnings = sanctions.warningsSince(party.kind(), party.id(), Instant.now().minus(java.time.Duration.ofDays(90)));
      return new PartyStatsDto(complaints90d == null ? 0 : complaints90d, warnings, removed == null ? 0 : removed);
   }

   private RelatedRequestDto related(Complaint complaint) {
      if (complaint.getRelatedRequestId() != null) {
         return jdbc.query("select id, text, status, created_at from part_requests where id = :id",
               Map.of("id", complaint.getRelatedRequestId()), (rs, n) -> new RelatedRequestDto("PART",
                     rs.getLong("id"), rs.getString("text"), rs.getString("status"), instant(rs, "created_at")))
               .stream().findFirst().orElse(null);
      }
      if (complaint.getRelatedServiceRequestId() != null) {
         return jdbc.query("select id, description, status, created_at from service_requests where id = :id",
               Map.of("id", complaint.getRelatedServiceRequestId()), (rs, n) -> new RelatedRequestDto("SERVICE",
                     rs.getLong("id"), rs.getString("description"), rs.getString("status"), instant(rs, "created_at")))
               .stream().findFirst().orElse(null);
      }
      return null;
   }

   private ReporterDto reporter(Complaint complaint) {
      if (complaint.getAuthorId() == null) {
         return new ReporterDto(null, null, null, complaint.getReporterRole());
      }
      return jdbc.query("select id, name, phone from users where id = :id", Map.of("id", complaint.getAuthorId()),
                  (rs, n) -> new ReporterDto(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
                        complaint.getReporterRole())).stream().findFirst()
            .orElse(new ReporterDto(complaint.getAuthorId(), null, null, complaint.getReporterRole()));
   }

   private List<PhotoDto> partPhotos(Long partId) {
      List<Long> ids = jdbc.queryForList("select media_id from part_photos where part_id = :id order by sort",
            Map.of("id", partId), Long.class);
      return List.copyOf(media.photos(ids).values());
   }

   private List<String> fitments(Long partId) {
      return jdbc.queryForList("""
            select b.name || coalesce(' ' || m.name || coalesce(' ' || m.generation, ''), '')
                   || case when f.year_from is null and f.year_to is null then ''
                           else ' · ' || coalesce(f.year_from::text, '…') || '–' || coalesce(f.year_to::text, '…') end
            from part_fitments f join brands b on b.id = f.brand_id left join models m on m.id = f.model_id
            where f.part_id = :id order by f.id""", Map.of("id", partId), String.class);
   }

   private String shopName(Long shopId) {
      return jdbc.queryForList("select name from shops where id = :id", Map.of("id", shopId), String.class).stream()
            .findFirst().orElse(null);
   }

   private Complaint complaint(Long id) {
      return complaints.findById(id).filter(found -> found.getType() != ComplaintType.CONTAINER_CLAIM)
            .orElseThrow(() -> new NotFoundException("COMPLAINT_NOT_FOUND", "Жалоба не найдена"));
   }

   private static PartyDto requireParty(Resolved target) {
      if (target.party == null) {
         throw new ConflictException("NO_PARTY", "Не нашли, кого наказывать: объект удалён или автор неизвестен");
      }
      return target.party;
   }

   private static void require(AuthPrincipal admin, AdminPermission permission) {
      if (!admin.can(permission)) {
         throw new ForbiddenException("FORBIDDEN", "Нет права " + permission + " для этого действия");
      }
   }

   private static String tags(ResultSet rs) throws SQLException {
      Array array = rs.getArray("tags");
      String[] values = array == null ? new String[0] : (String[]) array.getArray();
      return values.length == 0 ? null : String.join(", ", Arrays.asList(values));
   }

   private static Integer integer(ResultSet rs, String column) throws SQLException {
      int value = rs.getInt(column);
      return rs.wasNull() ? null : value;
   }

   private static Instant instant(ResultSet rs, String column) throws SQLException {
      var value = rs.getTimestamp(column);
      return value == null ? null : value.toInstant();
   }

   private static String nvl(String value, String fallback) {
      return value == null || value.isBlank() ? fallback : value;
   }

   private static String shorten(String value) {
      return shorten(value, 300);
   }

   private static String shorten(String value, int max) {
      return value.length() <= max ? value : value.substring(0, max - 1) + "…";
   }
}
