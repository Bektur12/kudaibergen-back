package kg.kudaibergen.shop.dispute;

import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.market.ContainerRepository;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.ShopVerificationService;
import kg.kudaibergen.shop.entity.MemberRole;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Споры за контейнер (ТЗ 7.3). Заявитель — тот, чьё место в приложении занято другим магазином.
 * Решает администрация: контейнер остаётся у того, кто стоит, или переходит заявителю — тогда стоявший
 * магазин отклоняется, а магазин заявителя (если есть) встаёт на место сразу.
 */
@Service
public class ContainerDisputeService {

   private final ContainerDisputeRepository disputes;
   private final ShopRepository shops;
   private final ShopAccess access;
   private final ShopVerificationService verification;
   private final ContainerRepository containers;
   private final MarketMapService market;
   private final UserRepository users;

   public ContainerDisputeService(ContainerDisputeRepository disputes, ShopRepository shops, ShopAccess access,
                                  ShopVerificationService verification, ContainerRepository containers,
                                  MarketMapService market, UserRepository users) {
      this.disputes = disputes;
      this.shops = shops;
      this.access = access;
      this.verification = verification;
      this.containers = containers;
      this.market = market;
      this.users = users;
   }

   /** «Это мой контейнер». Повторная заявка того же человека на тот же контейнер не плодит дублей. */
   @Transactional
   public ContainerDispute claim(Long userId, Long containerId, String text) {
      market.container(containerId);
      Shop current = shops.findStandingIn(containerId)
            .orElseThrow(() -> new ConflictException("CONTAINER_FREE",
                  "Контейнер свободен — зарегистрируйте магазин на нём"));
      Long claimantShopId = access.membership(userId)
            .filter(membership -> membership.member().getRole() == MemberRole.OWNER)
            .map(membership -> membership.shop().getId())
            .orElse(null);
      if (current.getId().equals(claimantShopId)) {
         throw new BadRequestException("OWN_CONTAINER", "В этом контейнере стоит ваш магазин");
      }
      return disputes.findFirstByContainerIdAndClaimantUserIdAndStatus(containerId, userId, DisputeStatus.OPEN)
            .orElseGet(() -> disputes.save(new ContainerDispute(containerId, userId, claimantShopId, current.getId(),
                  text == null || text.isBlank() ? null : text.strip())));
   }

   @Transactional
   public ContainerDispute resolve(Long disputeId, DisputeWinner winner, String comment, Long adminId) {
      ContainerDispute dispute = require(disputeId);
      if (!dispute.isOpen()) {
         throw new ConflictException("DISPUTE_RESOLVED", "Спор уже решён");
      }
      if (winner == DisputeWinner.CLAIMANT) {
         Long standing = shops.findStandingIn(dispute.getContainerId()).map(Shop::getId).orElse(null);
         Long claimantShop = dispute.getClaimantShopId() != null && shops.existsById(dispute.getClaimantShopId())
               ? dispute.getClaimantShopId() : null;
         verification.transferContainer(dispute.getContainerId(), standing, claimantShop, adminId, comment);
         if (dispute.getClaimantUserId() != null) {
            // администрация признала заявителя арендатором — код подтверждения пойдёт уже ему
            containers.findById(dispute.getContainerId()).ifPresent(container -> users
                  .findById(dispute.getClaimantUserId())
                  .ifPresent(user -> container.setTenantPhone(user.getPhone())));
            containers.flush();
            market.reload();
         }
      }
      dispute.resolve(winner, comment, adminId);
      return dispute;
   }

   @Transactional(readOnly = true)
   public ContainerDispute require(Long disputeId) {
      return disputes.findById(disputeId)
            .orElseThrow(() -> new NotFoundException("DISPUTE_NOT_FOUND", "Спор не найден"));
   }
}
