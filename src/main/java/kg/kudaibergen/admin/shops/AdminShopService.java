package kg.kudaibergen.admin.shops;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.admin.common.AdminNotices;
import kg.kudaibergen.admin.common.AdminNotices.StatusEvent;
import kg.kudaibergen.admin.common.AdminNotices.Target;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.sanctions.SanctionTarget;
import kg.kudaibergen.admin.sanctions.SanctionType;
import kg.kudaibergen.admin.sanctions.Sanctions;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminDisputeDto;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminDisputePartyDto;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminShopDetailDto;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminShopOwnerDto;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminShopRow;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminTenantDto;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminVerificationDto;
import kg.kudaibergen.admin.shops.AdminShopDtos.DisputeCounts;
import kg.kudaibergen.admin.shops.AdminShopDtos.ShopTabCounts;
import kg.kudaibergen.admin.shops.AdminShopDtos.TenantSide;
import kg.kudaibergen.admin.shops.AdminShopQueries.DisputeRowData;
import kg.kudaibergen.admin.shops.AdminShopQueries.ShopRowData;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.dto.LocationDto;
import jakarta.persistence.EntityManager;
import kg.kudaibergen.market.ContainerRepository;
import kg.kudaibergen.market.entity.Container;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.ShopVerificationRepository;
import kg.kudaibergen.shop.ShopVerificationService;
import kg.kudaibergen.shop.dispute.ContainerDispute;
import kg.kudaibergen.shop.dispute.ContainerDisputeService;
import kg.kudaibergen.shop.dispute.DisputeStatus;
import kg.kudaibergen.shop.dispute.DisputeWinner;
import kg.kudaibergen.shop.dto.SmsVerificationSentDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Раздел «Продавцы» [A2]: список с табами, карточка, решения по проверке, блокировка, предупреждение,
 * «Написать», споры за контейнер. Решения пишутся в журнал на уровне контроллера (@Audited).
 */
@Service
public class AdminShopService {

   private static final int VERIFICATIONS = 10;

   private final AdminShopQueries queries;
   private final ShopRepository shops;
   private final ShopVerificationRepository verifications;
   private final ShopVerificationService verification;
   private final ContainerDisputeService disputes;
   private final MarketMapService market;
   private final MediaService media;
   private final UserRepository users;
   private final Sanctions sanctions;
   private final AdminNotices notices;
   private final ContainerRepository containers;
   private final EntityManager entityManager;

   public AdminShopService(AdminShopQueries queries, ShopRepository shops,
                           ShopVerificationRepository verifications, ShopVerificationService verification,
                           ContainerDisputeService disputes, MarketMapService market, MediaService media,
                           UserRepository users, Sanctions sanctions, AdminNotices notices,
                           ContainerRepository containers, EntityManager entityManager) {
      this.queries = queries;
      this.shops = shops;
      this.verifications = verifications;
      this.verification = verification;
      this.disputes = disputes;
      this.market = market;
      this.media = media;
      this.users = users;
      this.sanctions = sanctions;
      this.notices = notices;
      this.containers = containers;
      this.entityManager = entityManager;
   }

   // ─────────────────────── список и карточка ───────────────────────

   @Transactional(readOnly = true)
   public AdminPage<AdminShopRow, ShopTabCounts> list(ShopTab tab, String q, String cursor, Integer limit) {
      int size = CursorPage.limit(limit);
      List<ShopRowData> rows = queries.page(tab, q, CursorPage.afterId(cursor), size);
      boolean more = rows.size() > size;
      List<ShopRowData> page = more ? rows.subList(0, size) : rows;
      Set<Long> warned = sanctions.warned(SanctionTarget.SHOP, page.stream().map(ShopRowData::id).toList());
      Map<Long, String> avatars = media.thumbUrls(page.stream().map(ShopRowData::avatarMediaId).toList());
      List<AdminShopRow> items = page.stream().map(row -> new AdminShopRow(row.id(), row.name(),
            row.avatarMediaId() == null ? null : avatars.get(row.avatarMediaId()), ShopStatus.valueOf(row.status()),
            warned.contains(row.id()), row.ownerPhone(), market.location(row.containerId()),
            row.pendingContainerId() == null ? null : market.location(row.pendingContainerId()), match(row),
            row.submittedAt(), row.createdAt(), row.openDisputes())).toList();
      String next = more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1).id())) : null;
      return new AdminPage<>(items, next, queries.counts(q));
   }

   @Transactional(readOnly = true)
   public AdminShopDetailDto detail(Long shopId) {
      // карточка собирается и SQL-запросами: сначала сбрасываем изменения этой транзакции
      entityManager.flush();
      Shop shop = shop(shopId);
      ShopRowData row = queries.one(shopId);
      User owner = users.findById(shop.getOwnerId()).orElseThrow();
      Long checked = shop.getPendingContainerId() != null ? shop.getPendingContainerId() : shop.getContainerId();
      // арендатор — из базы, а не из снимка карты: импорт и споры меняют его без перезагрузки схемы
      Container container = containers.findById(checked).orElseThrow();
      Instant since = Instant.now().minus(Duration.ofDays(90));
      List<AdminVerificationDto> history = verifications
            .findByShopIdOrderByCreatedAtDescIdDesc(shopId, PageRequest.of(0, VERIFICATIONS)).stream()
            .map(v -> new AdminVerificationDto(v.getId(), v.getMethod(), v.getStatus(), v.getReason(),
                  market.location(v.getContainerId()), v.getCreatedAt(), v.getDecidedAt()))
            .toList();
      return new AdminShopDetailDto(shop.getId(), shop.getPublicId(), shop.getName(),
            media.thumbUrl(shop.getAvatarMediaId()), shop.getStatus(), shop.getBlockReason(),
            !sanctions.warned(SanctionTarget.SHOP, List.of(shopId)).isEmpty(),
            new AdminShopOwnerDto(owner.getId(), owner.getName(), owner.getPhone()), shop.getPhone(),
            market.location(shop.getContainerId()),
            shop.getPendingContainerId() == null ? null : market.location(shop.getPendingContainerId()),
            new AdminTenantDto(container.getTenantName(), container.getTenantPhone(),
                  container.getTenantPhone() == null ? null : row.tenantIsMember()),
            match(row), queries.brandNames(shopId), queries.categoryNames(shopId), shop.getOpenFrom(),
            shop.getOpenTo(), shop.workDaySet(), shop.isOpen(), shop.getRating(), shop.getReviewsCount(),
            queries.activeParts(shopId), queries.members(shopId), shop.getCreatedAt(), shop.getVerifiedAt(), history,
            disputesOf(shopId), queries.complaints90d(shopId),
            sanctions.warningsSince(SanctionTarget.SHOP, shopId, since), sanctions.history(SanctionTarget.SHOP, shopId));
   }

   // ─────────────────────── решения ───────────────────────

   @Transactional
   public void approve(Long shopId, Long adminId) {
      verification.adminApprove(shopId, adminId);
      notices.status(queries.memberIds(shopId), Target.SHOP, shopId, StatusEvent.APPROVED, null);
   }

   @Transactional
   public void reject(Long shopId, Long adminId, String reason) {
      verification.adminReject(shopId, adminId, reason);
      notices.status(queries.memberIds(shopId), Target.SHOP, shopId, StatusEvent.REJECTED, reason);
   }

   public SmsVerificationSentDto sendSmsCode(Long shopId, String clientIp) {
      Shop shop = shop(shopId);
      Lang lang = users.findById(shop.getOwnerId()).map(User::getLang).orElse(Lang.RU);
      return verification.adminSendSms(shopId, clientIp, lang);
   }

   @Transactional
   public void block(Long shopId, Long adminId, String reason) {
      verification.block(shopId, reason);
      sanctions.record(SanctionTarget.SHOP, shopId, SanctionType.BLOCK, reason, adminId);
      notices.status(queries.memberIds(shopId), Target.SHOP, shopId, StatusEvent.BLOCKED, reason);
   }

   @Transactional
   public void unblock(Long shopId, Long adminId) {
      verification.unblock(shopId);
      sanctions.record(SanctionTarget.SHOP, shopId, SanctionType.UNBLOCK, null, adminId);
      notices.status(queries.memberIds(shopId), Target.SHOP, shopId, StatusEvent.UNBLOCKED, null);
   }

   @Transactional
   public void warn(Long shopId, Long adminId, String reason) {
      shop(shopId);
      sanctions.record(SanctionTarget.SHOP, shopId, SanctionType.WARNING, reason, adminId);
      notices.warning(queries.memberIds(shopId), reason);
   }

   /** «Написать»: пуш всем людям бокса. */
   @Transactional(readOnly = true)
   public int message(Long shopId, String text) {
      shop(shopId);
      List<Long> recipients = queries.memberIds(shopId);
      notices.message(recipients, text.strip());
      return recipients.size();
   }

   // ─────────────────────── споры ───────────────────────

   @Transactional(readOnly = true)
   public AdminPage<AdminDisputeDto, DisputeCounts> disputes(DisputeStatus status, String cursor, Integer limit) {
      int size = CursorPage.limit(limit);
      List<DisputeRowData> rows = queries.disputes(status, null, null, CursorPage.afterId(cursor), size);
      boolean more = rows.size() > size;
      List<DisputeRowData> page = more ? rows.subList(0, size) : rows;
      String next = more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1).id())) : null;
      return new AdminPage<>(page.stream().map(this::dispute).toList(), next, queries.disputeCounts());
   }

   @Transactional(readOnly = true)
   public AdminDisputeDto dispute(Long disputeId) {
      entityManager.flush();
      return queries.disputes(null, null, disputeId, 0, 1).stream().findFirst().map(this::dispute)
            .orElseThrow(() -> new NotFoundException("DISPUTE_NOT_FOUND", "Спор не найден"));
   }

   /** Решение спора; обе стороны получают пуш. */
   @Transactional
   public AdminDisputeDto resolve(Long disputeId, DisputeWinner winner, String comment, Long adminId) {
      ContainerDispute dispute = disputes.resolve(disputeId, winner, comment, adminId);
      LocationDto location = market.location(dispute.getContainerId());
      String label = location.rowLabel() + " · " + location.number();
      List<Long> current = dispute.getCurrentShopId() == null ? List.of() : queries.memberIds(dispute.getCurrentShopId());
      List<Long> claimant = dispute.getClaimantUserId() == null ? List.of() : List.of(dispute.getClaimantUserId());
      notices.disputeResolved(winner == DisputeWinner.CLAIMANT ? claimant : current, disputeId, true, label, comment);
      notices.disputeResolved(winner == DisputeWinner.CLAIMANT ? current : claimant, disputeId, false, label, comment);
      return dispute(disputeId);
   }

   private List<AdminDisputeDto> disputesOf(Long shopId) {
      return queries.disputes(DisputeStatus.OPEN, shopId, null, 0, 20).stream().map(this::dispute).toList();
   }

   private AdminDisputeDto dispute(DisputeRowData row) {
      TenantSide side;
      if (row.tenantPhone() == null) {
         side = TenantSide.UNKNOWN;
      } else if (row.tenantPhone().equals(row.claimantPhone())) {
         side = TenantSide.CLAIMANT;
      } else if (row.tenantPhone().equals(row.currentPhone())) {
         side = TenantSide.CURRENT;
      } else {
         side = TenantSide.NONE;
      }
      return new AdminDisputeDto(row.id(), DisputeStatus.valueOf(row.status()), market.location(row.containerId()),
            new AdminTenantDto(row.tenantName(), row.tenantPhone(), null), side,
            new AdminDisputePartyDto(row.claimantUserId(), row.claimantName(), row.claimantPhone(),
                  row.claimantShopId(), row.claimantShopName()),
            new AdminDisputePartyDto(row.currentUserId(), row.currentUserName(), row.currentPhone(),
                  row.currentShopId(), row.currentShopName()),
            row.text(), row.winner() == null ? null : DisputeWinner.valueOf(row.winner()), row.resolution(),
            row.createdAt(), row.resolvedAt());
   }

   static TenantMatch match(ShopRowData row) {
      return TenantMatch.of(row.openDisputes() > 0, row.smsConfirmed(), row.tenantPhoneKnown(), row.tenantIsMember());
   }

   private Shop shop(Long shopId) {
      return shops.findById(shopId).orElseThrow(() -> new NotFoundException("SHOP_NOT_FOUND", "Магазин не найден"));
   }
}
