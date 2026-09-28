package kg.kudaibergen.shop;

import java.util.Optional;

import kg.kudaibergen.auth.otp.OtpPurpose;
import kg.kudaibergen.auth.otp.OtpService;
import kg.kudaibergen.auth.sms.SmsProvider;
import kg.kudaibergen.auth.sms.SmsTexts;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.shop.dto.SmsVerificationSentDto;
import kg.kudaibergen.shop.dto.VerificationDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.shop.entity.ShopVerification;
import kg.kudaibergen.shop.entity.VerificationMethod;
import kg.kudaibergen.shop.entity.VerificationStatus;
import kg.kudaibergen.user.entity.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Проверка, что продавец действительно стоит в контейнере (ТЗ 7.2), — достаточно одного способа:
 * <ul>
 *    <li>QR-наклейка на контейнере + GPS в пределах рынка — мгновенно;</li>
 *    <li>SMS-код на номер арендатора из базы рынка;</li>
 *    <li>заявка администрации — админ сверяет со списком арендаторов.</li>
 * </ul>
 * Та же проверка нужна при переезде: до неё магазин остаётся на старом месте.
 */
@Service
public class ShopVerificationService {

   private static final Logger log = LoggerFactory.getLogger(ShopVerificationService.class);

   private final ShopRepository shops;
   private final ShopVerificationRepository verifications;
   private final ShopAccess access;
   private final MarketMapService market;
   private final OtpService otp;
   private final SmsProvider sms;
   private final boolean exposeCode;

   public ShopVerificationService(ShopRepository shops, ShopVerificationRepository verifications, ShopAccess access,
                                  MarketMapService market, OtpService otp, SmsProvider sms,
                                  AppProperties properties) {
      this.shops = shops;
      this.verifications = verifications;
      this.access = access;
      this.market = market;
      this.otp = otp;
      this.sms = sms;
      this.exposeCode = properties.otp().exposeCode();
   }

   // ─────────────────────── продавец ───────────────────────

   @Transactional(readOnly = true)
   public VerificationDto status(Shop shop) {
      Long target = shop.containerToVerify();
      Optional<ShopVerification> last = verifications.findFirstByShopIdOrderByCreatedAtDescIdDesc(shop.getId());
      String tenantPhone = target == null ? null : market.container(target).container().getTenantPhone();
      boolean waiting = last.filter(v -> v.getStatus() == VerificationStatus.PENDING && v.getContainerId().equals(target))
            .isPresent();
      return new VerificationDto(target != null, target == null ? null : market.location(target), tenantPhone != null,
            mask(tenantPhone), last.map(ShopVerification::getStatus).orElse(null),
            last.map(ShopVerification::getMethod).orElse(null), last.map(ShopVerification::getReason).orElse(null),
            waiting);
   }

   /** Скан наклейки своего контейнера. GPS должен быть на рынке (если карта привязана к GPS). */
   @Transactional
   public void verifyByQr(Long userId, String qrToken, Double lat, Double lon) {
      Shop shop = ownerShop(userId);
      MarketSnapshot.ContainerView target = market.container(requireTarget(shop));
      if (!target.container().getQrToken().equals(qrToken)) {
         throw new BadRequestException("QR_MISMATCH",
               "Это не наклейка вашего контейнера: нужен " + target.row().row().getLabel() + " · Бокс "
                     + target.container().getNumber());
      }
      Optional<Boolean> inside = lat == null || lon == null ? Optional.empty() : market.insideMarket(lat, lon);
      if (lat == null || lon == null) {
         if (market.snapshot().calibration().isPresent()) {
            throw new BadRequestException("LOCATION_REQUIRED", "Включите геолокацию: нужно убедиться, что вы на рынке");
         }
      } else if (inside.isPresent() && !inside.get()) {
         throw new BadRequestException("NOT_AT_MARKET", "Вы не на рынке — отсканируйте наклейку на месте");
      }
      approve(shop, target.container().getId(), VerificationMethod.QR, null);
   }

   /** Код на номер арендатора, закреплённый за контейнером в базе рынка. */
   public SmsVerificationSentDto sendSms(Long userId, String clientIp, Lang lang) {
      Shop shop = ownerShop(userId);
      MarketSnapshot.ContainerView target = market.container(requireTarget(shop));
      String tenantPhone = target.container().getTenantPhone();
      if (tenantPhone == null) {
         throw new ConflictException("SMS_UNAVAILABLE",
               "Для этого контейнера нет номера арендатора — отсканируйте QR или попросите администрацию");
      }
      String code = otp.issue(OtpPurpose.SHOP_VERIFY, tenantPhone, clientIp);
      sms.send(tenantPhone, SmsTexts.shopVerificationCode(code,
            target.row().row().getLabel() + " · Бокс " + target.container().getNumber(), lang));
      return new SmsVerificationSentDto(mask(tenantPhone), otp.codeTtl().toSeconds(),
            otp.resendInterval().toSeconds(), exposeCode ? code : null);
   }

   @Transactional
   public void confirmSms(Long userId, String code) {
      Shop shop = ownerShop(userId);
      MarketSnapshot.ContainerView target = market.container(requireTarget(shop));
      String tenantPhone = target.container().getTenantPhone();
      if (tenantPhone == null) {
         throw new ConflictException("SMS_UNAVAILABLE", "Для этого контейнера нет номера арендатора");
      }
      otp.verify(OtpPurpose.SHOP_VERIFY, tenantPhone, code);
      approve(shop, target.container().getId(), VerificationMethod.SMS, null);
   }

   /** Нет наклейки — заявка в админку (до 1 рабочего дня). Повторная заявка не плодит дублей. */
   @Transactional
   public void requestAdmin(Long userId) {
      Shop shop = ownerShop(userId);
      Long target = requireTarget(shop);
      if (verifications.findFirstByShopIdAndContainerIdAndStatusOrderByCreatedAtDesc(shop.getId(), target,
            VerificationStatus.PENDING).isEmpty()) {
         verifications.save(new ShopVerification(shop.getId(), target, VerificationMethod.ADMIN));
      }
   }

   /** Переезд: новое место ждёт проверки, до неё магазин остаётся на старом. */
   @Transactional
   public void relocate(Long userId, Long containerId) {
      Shop shop = ownerShop(userId);
      if (containerId.equals(shop.getContainerId())) {
         throw new BadRequestException("SAME_CONTAINER", "Магазин уже стоит в этом контейнере");
      }
      market.container(containerId);
      if (shops.isContainerTaken(containerId)) {
         throw ShopService.containerTaken();
      }
      if (shop.getStatus() == ShopStatus.PENDING_VERIFICATION) {
         // ещё нигде не подтверждён — просто выбрал другой контейнер, проверять будем уже его
         shop.moveUnverified(containerId);
      } else {
         shop.setPendingContainerId(containerId);
      }
      try {
         shops.saveAndFlush(shop);
      } catch (DataIntegrityViolationException race) {
         throw ShopService.containerTaken();
      }
   }

   @Transactional
   public void cancelRelocation(Long userId) {
      ownerShop(userId).setPendingContainerId(null);
   }

   // ─────────────────────── администрация ───────────────────────

   @Transactional
   public void adminApprove(Long shopId, Long adminId) {
      Shop shop = shop(shopId);
      Long target = requireTarget(shop);
      verifications.findFirstByShopIdAndContainerIdAndStatusOrderByCreatedAtDesc(shopId, target,
                  VerificationStatus.PENDING)
            .ifPresentOrElse(pending -> {
               pending.approve(adminId);
               shop.verified();
            }, () -> approve(shop, target, VerificationMethod.ADMIN, adminId));
      log.info("Админ {} подтвердил магазин {} в контейнере {}", adminId, shopId, target);
   }

   /** Отказ: магазин остаётся на проверке (или на старом месте при переезде), продавец видит причину. */
   @Transactional
   public void adminReject(Long shopId, Long adminId, String reason) {
      Shop shop = shop(shopId);
      Long target = requireTarget(shop);
      ShopVerification verification = verifications.findFirstByShopIdAndContainerIdAndStatusOrderByCreatedAtDesc(
                  shopId, target, VerificationStatus.PENDING)
            .orElseGet(() -> verifications.save(new ShopVerification(shopId, target, VerificationMethod.ADMIN)));
      verification.reject(adminId, reason);
   }

   @Transactional
   public void block(Long shopId, String reason) {
      shop(shopId).block(reason);
   }

   @Transactional
   public void unblock(Long shopId) {
      Shop shop = shop(shopId);
      if (shop.getStatus() != ShopStatus.BLOCKED) {
         throw new ConflictException("NOT_BLOCKED", "Магазин не заблокирован");
      }
      shop.unblock();
   }

   // ─────────────────────── внутреннее ───────────────────────

   private void approve(Shop shop, Long containerId, VerificationMethod method, Long decidedBy) {
      ShopVerification verification = new ShopVerification(shop.getId(), containerId, method);
      verification.approve(decidedBy);
      verifications.save(verification);
      shop.verified();
   }

   private Shop ownerShop(Long userId) {
      Shop shop = access.requireOwner(userId).shop();
      if (shop.getStatus() == ShopStatus.BLOCKED) {
         throw new ConflictException("SHOP_BLOCKED", "Магазин заблокирован: " + shop.getBlockReason());
      }
      return shop;
   }

   private Shop shop(Long shopId) {
      return shops.findById(shopId).orElseThrow(() -> new NotFoundException("SHOP_NOT_FOUND", "Магазин не найден"));
   }

   private static Long requireTarget(Shop shop) {
      Long target = shop.containerToVerify();
      if (target == null) {
         throw new ConflictException("NOTHING_TO_VERIFY", "Магазин уже подтверждён на своём месте");
      }
      return target;
   }

   /** +996555123411 → +996 555 ••• •11 */
   static String mask(String phone) {
      if (phone == null || phone.length() < 13) {
         return phone;
      }
      return phone.substring(0, 4) + " " + phone.substring(4, 7) + " ••• •" + phone.substring(11);
   }
}
