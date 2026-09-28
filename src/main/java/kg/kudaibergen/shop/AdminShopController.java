package kg.kudaibergen.shop;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopStatus;
import kg.kudaibergen.shop.entity.ShopVerification;
import kg.kudaibergen.shop.entity.VerificationMethod;
import kg.kudaibergen.shop.entity.VerificationStatus;
import kg.kudaibergen.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Админка рынка: подтверждение продавцов и блокировка магазинов (ТЗ, раздел 2). */
@RestController
@RequestMapping("/api/v1/admin/shops")
@Tag(name = "Админка: магазины")
public class AdminShopController {

   private static final int PAGE = 100;

   private final ShopRepository shops;
   private final ShopVerificationRepository verifications;
   private final ShopVerificationService verification;
   private final ShopMapper mapper;
   private final UserRepository users;

   public AdminShopController(ShopRepository shops, ShopVerificationRepository verifications,
                              ShopVerificationService verification, ShopMapper mapper, UserRepository users) {
      this.shops = shops;
      this.verifications = verifications;
      this.verification = verification;
      this.mapper = mapper;
      this.users = users;
   }

   @GetMapping
   @Transactional(readOnly = true)
   @Operation(summary = "Магазины по статусу", description = "PENDING_VERIFICATION — ждут проверки, BLOCKED — заблокированы")
   public List<AdminShopDto> list(@RequestParam(defaultValue = "PENDING_VERIFICATION") ShopStatus status) {
      return shops.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, PAGE)).stream().map(this::toDto).toList();
   }

   @GetMapping("/verification-queue")
   @Transactional(readOnly = true)
   @Operation(summary = "Заявки «подтвердите меня»", description = "Сверить со списком арендаторов и подтвердить")
   public List<AdminShopDto> queue() {
      return verifications.findByStatusOrderByCreatedAtAsc(VerificationStatus.PENDING, PageRequest.of(0, PAGE)).stream()
            .map(ShopVerification::getShopId)
            .distinct()
            .map(id -> shops.findById(id).orElseThrow())
            .map(this::toDto)
            .toList();
   }

   @PostMapping("/{id}/approve")
   @Operation(summary = "Подтвердить место продавца", description = "Новый магазин становится видимым; при переезде — занимает новое место")
   public AdminShopDto approve(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin) {
      verification.adminApprove(id, admin.userId());
      return reload(id);
   }

   @PostMapping("/{id}/reject")
   @Operation(summary = "Отказать в подтверждении", description = "Продавец увидит причину")
   public AdminShopDto reject(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                              @Valid @RequestBody ReasonRequest request) {
      verification.adminReject(id, admin.userId(), request.reason());
      return reload(id);
   }

   @PostMapping("/{id}/block")
   @Operation(summary = "Заблокировать магазин", description = "Скрывает магазин и товары, запросы не приходят")
   public AdminShopDto block(@PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
      verification.block(id, request.reason());
      return reload(id);
   }

   @PostMapping("/{id}/unblock")
   @Operation(summary = "Снять блокировку")
   public AdminShopDto unblock(@PathVariable Long id) {
      verification.unblock(id);
      return reload(id);
   }

   private AdminShopDto reload(Long id) {
      return toDto(shops.findById(id).orElseThrow());
   }

   private AdminShopDto toDto(Shop shop) {
      var last = verifications.findFirstByShopIdOrderByCreatedAtDescIdDesc(shop.getId());
      return new AdminShopDto(shop.getId(), shop.getName(), shop.getStatus(), shop.getBlockReason(),
            mapper.location(shop.getContainerId()),
            shop.getPendingContainerId() == null ? null : mapper.location(shop.getPendingContainerId()),
            users.findById(shop.getOwnerId()).map(u -> u.getPhone()).orElse(null),
            last.map(ShopVerification::getMethod).orElse(null), last.map(ShopVerification::getStatus).orElse(null),
            shop.getCreatedAt());
   }

   public record ReasonRequest(@NotBlank(message = "Укажите причину") @Size(max = 300) String reason) {
   }

   /** Магазин для админки: место, куда переезжает, телефон владельца, последняя проверка. */
   public record AdminShopDto(Long id, String name, ShopStatus status, String blockReason, LocationDto location,
                              LocationDto pendingLocation, String ownerPhone, VerificationMethod lastMethod,
                              VerificationStatus lastStatus, Instant createdAt) {
   }
}
