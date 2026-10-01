package kg.kudaibergen.admin.shops;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminDisputeDto;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminMessageRequest;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminMessageSentDto;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminShopDetailDto;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminShopRow;
import kg.kudaibergen.admin.shops.AdminShopDtos.DisputeCounts;
import kg.kudaibergen.admin.shops.AdminShopDtos.ResolveDisputeRequest;
import kg.kudaibergen.admin.shops.AdminShopDtos.ShopReasonRequest;
import kg.kudaibergen.admin.shops.AdminShopDtos.ShopTabCounts;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.shop.dispute.DisputeStatus;
import kg.kudaibergen.shop.dto.SmsVerificationSentDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Продавцы [A2]: проверка мест, блокировка, предупреждения, «Написать», споры за контейнер. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Админка: продавцы")
public class AdminShopController {

   private final AdminShopService shops;

   public AdminShopController(AdminShopService shops) {
      this.shops = shops;
   }

   @GetMapping("/shops")
   @PreAuthorize("hasAuthority('SELLERS_VIEW')")
   @Operation(summary = "Список продавцов с табами",
         description = "q — название, цифры телефона владельца или «14 12». counts — по всем табам с учётом q")
   public AdminPage<AdminShopRow, ShopTabCounts> list(@RequestParam(defaultValue = "ALL") ShopTab tab,
                                                      @RequestParam(required = false) String q,
                                                      @RequestParam(required = false) String cursor,
                                                      @RequestParam(required = false) Integer limit) {
      return shops.list(tab, q, cursor, limit);
   }

   @GetMapping("/shops/{id}")
   @PreAuthorize("hasAuthority('SELLERS_VIEW')")
   @Operation(summary = "Карточка продавца (правая панель)")
   public AdminShopDetailDto detail(@PathVariable Long id) {
      return shops.detail(id);
   }

   @PostMapping("/shops/{id}/approve")
   @PreAuthorize("hasAuthority('SELLERS_VERIFY')")
   @Audited(action = "SHOP_APPROVE", entity = "SHOP", id = "#id")
   @Operation(summary = "Подтвердить место", description = "Новый или отклонённый магазин становится действующим, "
         + "переезд — занимает новое место. Продавцу — пуш ACCOUNT_STATUS")
   @ApiResponse(responseCode = "409", description = "NOTHING_TO_VERIFY, CONTAINER_TAKEN")
   public AdminShopDetailDto approve(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin) {
      AuditTrail.before(shops.detail(id));
      shops.approve(id, admin.userId());
      return shops.detail(id);
   }

   @PostMapping("/shops/{id}/reject")
   @PreAuthorize("hasAuthority('SELLERS_VERIFY')")
   @Audited(action = "SHOP_REJECT", entity = "SHOP", id = "#id", comment = "#request.reason")
   @Operation(summary = "Отклонить", description = "Новый магазин — REJECTED: скрыт, контейнер свободен, продавец видит "
         + "причину и выбирает место заново. Переезд — отменяется")
   @ApiResponse(responseCode = "409", description = "NOTHING_TO_VERIFY, ALREADY_REJECTED")
   public AdminShopDetailDto reject(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                                    @Valid @RequestBody ShopReasonRequest request) {
      AuditTrail.before(shops.detail(id));
      shops.reject(id, admin.userId(), request.reason());
      return shops.detail(id);
   }

   @PostMapping("/shops/{id}/send-sms-code")
   @PreAuthorize("hasAuthority('SELLERS_VERIFY')")
   @Audited(action = "SHOP_SMS_CODE", entity = "SHOP", id = "#id")
   @Operation(summary = "Отправить SMS-код арендатору", description = "Код уходит на телефон арендатора проверяемого "
         + "контейнера; продавец вводит его в приложении (экран проверки)")
   @ApiResponse(responseCode = "409", description = "NOTHING_TO_VERIFY, SMS_UNAVAILABLE — нет телефона арендатора")
   @ApiResponse(responseCode = "429", description = "OTP_COOLDOWN, OTP_RATE_LIMITED")
   public SmsVerificationSentDto sendSmsCode(@PathVariable Long id, HttpServletRequest http) {
      return shops.sendSmsCode(id, http.getRemoteAddr());
   }

   @PostMapping("/shops/{id}/block")
   @PreAuthorize("hasAuthority('SELLERS_BLOCK')")
   @Audited(action = "SHOP_BLOCK", entity = "SHOP", id = "#id", comment = "#request.reason")
   @Operation(summary = "Заблокировать", description = "Скрывает магазин и товары, запросы не приходят")
   @ApiResponse(responseCode = "409", description = "ALREADY_BLOCKED, SHOP_REJECTED")
   public AdminShopDetailDto block(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                                   @Valid @RequestBody ShopReasonRequest request) {
      AuditTrail.before(shops.detail(id));
      shops.block(id, admin.userId(), request.reason());
      return shops.detail(id);
   }

   @PostMapping("/shops/{id}/unblock")
   @PreAuthorize("hasAuthority('SELLERS_BLOCK')")
   @Audited(action = "SHOP_UNBLOCK", entity = "SHOP", id = "#id")
   @Operation(summary = "Снять блокировку")
   @ApiResponse(responseCode = "409", description = "NOT_BLOCKED")
   public AdminShopDetailDto unblock(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin) {
      AuditTrail.before(shops.detail(id));
      shops.unblock(id, admin.userId());
      return shops.detail(id);
   }

   @PostMapping("/shops/{id}/warn")
   @PreAuthorize("hasAuthority('SELLER_WARN')")
   @Audited(action = "SHOP_WARN", entity = "SHOP", id = "#id", comment = "#request.reason")
   @Operation(summary = "Предупредить", description = "Пуш с причиной; 90 дней магазин отмечен «Предупреждён»")
   public AdminShopDetailDto warn(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                                  @Valid @RequestBody ShopReasonRequest request) {
      shops.warn(id, admin.userId(), request.reason());
      return shops.detail(id);
   }

   @PostMapping("/shops/{id}/message")
   @PreAuthorize("hasAuthority('MESSAGE_USERS')")
   @Audited(action = "SHOP_MESSAGE", entity = "SHOP", id = "#id", comment = "#request.text")
   @Operation(summary = "Написать продавцу", description = "Пуш ADMIN_MESSAGE всем людям бокса; ответить нельзя")
   public AdminMessageSentDto message(@PathVariable Long id, @Valid @RequestBody AdminMessageRequest request) {
      return new AdminMessageSentDto(shops.message(id, request.text()));
   }

   // ─────────────────────── споры ───────────────────────

   @GetMapping("/disputes")
   @PreAuthorize("hasAuthority('SELLERS_VIEW')")
   @Operation(summary = "Споры за контейнер", description = "status не задан — все; counts — открытые и решённые")
   public AdminPage<AdminDisputeDto, DisputeCounts> disputes(@RequestParam(required = false) DisputeStatus status,
                                                             @RequestParam(required = false) String cursor,
                                                             @RequestParam(required = false) Integer limit) {
      return shops.disputes(status, cursor, limit);
   }

   @GetMapping("/disputes/{id}")
   @PreAuthorize("hasAuthority('SELLERS_VIEW')")
   @Operation(summary = "Спор за контейнер")
   public AdminDisputeDto dispute(@PathVariable Long id) {
      return shops.dispute(id);
   }

   @PostMapping("/disputes/{id}/resolve")
   @PreAuthorize("hasAuthority('DISPUTES_RESOLVE')")
   @Audited(action = "DISPUTE_RESOLVE", entity = "DISPUTE", id = "#id", comment = "#request.comment")
   @Operation(summary = "Решить спор", description = "CURRENT — контейнер остаётся у того, кто стоит. CLAIMANT — "
         + "стоявший магазин отклоняется, магазин заявителя (если есть) встаёт на место, телефон заявителя "
         + "становится телефоном арендатора. Обеим сторонам — пуш DISPUTE_RESOLVED")
   @ApiResponse(responseCode = "409", description = "DISPUTE_RESOLVED, SHOP_BLOCKED, CONTAINER_TAKEN")
   public AdminDisputeDto resolve(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                                  @Valid @RequestBody ResolveDisputeRequest request) {
      AuditTrail.before(shops.dispute(id));
      return shops.resolve(id, request.winner(), request.comment(), admin.userId());
   }
}
