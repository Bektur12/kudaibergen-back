package kg.kudaibergen.shop;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.shop.dto.MemberDto;
import kg.kudaibergen.shop.dto.MyShopDto;
import kg.kudaibergen.shop.dto.ShopPhotoDto;
import kg.kudaibergen.shop.dto.ShopRequests;
import kg.kudaibergen.shop.dto.SmsVerificationSentDto;
import kg.kudaibergen.shop.dto.VerificationDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Кабинет продавца: регистрация бокса, «Мой бокс», проверка места, сотрудники. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Мой бокс")
public class MyShopController {

   private final ShopService shops;
   private final ShopVerificationService verification;
   private final ShopAccess access;
   private final ShopPhotoService photos;

   public MyShopController(ShopService shops, ShopVerificationService verification, ShopAccess access,
                           ShopPhotoService photos) {
      this.shops = shops;
      this.verification = verification;
      this.access = access;
      this.photos = photos;
   }

   @PostMapping("/shops")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Зарегистрировать бокс (10а → 10, «Сохранить»)", description = """
         Контейнер из сетки 10а + название (2–60, без телефонов и ссылок), марки (≥ 1), категории (≥ 1), часы.
         Магазин получает статус «На проверке»: можно заполнять каталог, но покупатели его не видят.""")
   @ApiResponse(responseCode = "409", description = "CONTAINER_TAKEN (→ «Это мой контейнер»), ALREADY_IN_SHOP")
   public MyShopDto register(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody ShopRequests.CreateShop request,
                             @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                   required = false) String language) {
      return shops.register(principal.userId(), request, Langs.fromHeader(language));
   }

   @GetMapping("/my/shop")
   @Operation(summary = "Мой бокс (10, 21, 22)", description = "404 NO_SHOP — у пользователя нет бокса")
   public MyShopDto myShop(@AuthenticationPrincipal AuthPrincipal principal,
                           @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                 required = false) String language) {
      return shops.myShop(principal.userId(), Langs.fromHeader(language));
   }

   @PatchMapping("/my/shop")
   @Operation(summary = "Название, часы, выходные, телефон (владелец)")
   public MyShopDto update(@AuthenticationPrincipal AuthPrincipal principal,
                           @Valid @RequestBody ShopRequests.UpdateShop request,
                           @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                 required = false) String language) {
      return shops.update(principal.userId(), request, Langs.fromHeader(language));
   }

   @PutMapping("/my/shop/brands")
   @Operation(summary = "Марки, которые продаю (23, владелец)", description = "Запросы приходят только по ним")
   public MyShopDto brands(@AuthenticationPrincipal AuthPrincipal principal,
                           @Valid @RequestBody ShopRequests.SetBrands request,
                           @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                 required = false) String language) {
      return shops.setBrands(principal.userId(), request.brandIds(), Langs.fromHeader(language));
   }

   @PutMapping("/my/shop/categories")
   @Operation(summary = "Что продаю (10, владелец)")
   public MyShopDto categories(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody ShopRequests.SetCategories request,
                               @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                     required = false) String language) {
      return shops.setCategories(principal.userId(), request.categoryIds(), Langs.fromHeader(language));
   }

   @PatchMapping("/my/shop/open")
   @Operation(summary = "Тумблер «Бокс закрыт» (21)", description = "Может и сотрудник. Закрыт — запросы не приходят")
   public MyShopDto open(@AuthenticationPrincipal AuthPrincipal principal,
                         @Valid @RequestBody ShopRequests.SetOpen request,
                         @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                               required = false) String language) {
      return shops.setOpen(principal.userId(), request.isOpen(), Langs.fromHeader(language));
   }

   // ─────────────────────── аватар и фото места ───────────────────────

   @PutMapping("/my/shop/avatar")
   @Operation(summary = "Аватар магазина (22, владелец)", description = "mediaId из POST /media/photos (purpose AVATAR)")
   public MyShopDto avatar(@AuthenticationPrincipal AuthPrincipal principal,
                           @Valid @RequestBody ShopRequests.MediaRef request,
                           @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                 required = false) String language) {
      photos.setAvatar(principal.userId(), request.mediaId());
      return shops.myShop(principal.userId(), Langs.fromHeader(language));
   }

   @DeleteMapping("/my/shop/avatar")
   @Operation(summary = "Убрать аватар", description = "Покупатели увидят первую букву названия")
   public MyShopDto removeAvatar(@AuthenticationPrincipal AuthPrincipal principal,
                                 @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                       required = false) String language) {
      photos.removeAvatar(principal.userId());
      return shops.myShop(principal.userId(), Langs.fromHeader(language));
   }

   @GetMapping("/my/shop/photos")
   @Operation(summary = "Фото места (22)", description = "Первое — «Обложка», до 8")
   public List<ShopPhotoDto> photos(@AuthenticationPrincipal AuthPrincipal principal) {
      return photos.mine(principal.userId());
   }

   @PostMapping("/my/shop/photos")
   @Operation(summary = "Добавить фото места («Сфотать» / «Из галереи», владелец)",
         description = "mediaId из POST /media/photos (purpose SHOP). 409 PHOTOS_LIMIT — уже 8")
   public List<ShopPhotoDto> addPhoto(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody ShopRequests.MediaRef request) {
      return photos.add(principal.userId(), request.mediaId());
   }

   @PutMapping("/my/shop/photos/order")
   @Operation(summary = "Порядок фото (перетаскивание)", description = "Все id фото места в новом порядке")
   public List<ShopPhotoDto> reorderPhotos(@AuthenticationPrincipal AuthPrincipal principal,
                                           @Valid @RequestBody ShopRequests.PhotoOrder request) {
      return photos.reorder(principal.userId(), request.mediaIds());
   }

   @PostMapping("/my/shop/photos/{mediaId}/cover")
   @Operation(summary = "Сделать обложкой")
   public List<ShopPhotoDto> makeCover(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long mediaId) {
      return photos.makeCover(principal.userId(), mediaId);
   }

   @DeleteMapping("/my/shop/photos/{mediaId}")
   @Operation(summary = "Удалить фото места")
   public List<ShopPhotoDto> removePhoto(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable Long mediaId) {
      return photos.remove(principal.userId(), mediaId);
   }

   // ─────────────────────── проверка места ───────────────────────

   @GetMapping("/my/shop/verification")
   @Operation(summary = "Статус проверки места", description = "Экраны «Сканируйте QR на контейнере» и «Магазин на проверке»")
   public VerificationDto verificationStatus(@AuthenticationPrincipal AuthPrincipal principal) {
      return verification.status(access.requireMember(principal.userId()).shop());
   }

   @PostMapping("/my/shop/verification/qr")
   @Operation(summary = "Подтвердить место QR-наклейкой", description = "Мгновенно. GPS должен быть в пределах рынка")
   @ApiResponse(responseCode = "400", description = "QR_MISMATCH, NOT_AT_MARKET, LOCATION_REQUIRED")
   public VerificationDto verifyQr(@AuthenticationPrincipal AuthPrincipal principal,
                                   @Valid @RequestBody ShopRequests.QrVerification request) {
      verification.verifyByQr(principal.userId(), request.qrToken(), request.lat(), request.lon());
      return verificationStatus(principal);
   }

   @PostMapping("/my/shop/verification/sms/send")
   @Operation(summary = "Код на номер арендатора контейнера", description = "409 SMS_UNAVAILABLE — номера в базе рынка нет")
   public SmsVerificationSentDto sendSms(@AuthenticationPrincipal AuthPrincipal principal, HttpServletRequest http,
                                         @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                               required = false) String language) {
      return verification.sendSms(principal.userId(), http.getRemoteAddr(), Langs.fromHeader(language));
   }

   @PostMapping("/my/shop/verification/sms/confirm")
   @Operation(summary = "Подтвердить место кодом арендатора")
   public VerificationDto confirmSms(@AuthenticationPrincipal AuthPrincipal principal,
                                     @Valid @RequestBody ShopRequests.SmsConfirm request) {
      verification.confirmSms(principal.userId(), request.code());
      return verificationStatus(principal);
   }

   @PostMapping("/my/shop/verification/admin-request")
   @Operation(summary = "Попросить администрацию подтвердить", description = "Если нет наклейки; до 1 рабочего дня")
   public VerificationDto requestAdmin(@AuthenticationPrincipal AuthPrincipal principal) {
      verification.requestAdmin(principal.userId());
      return verificationStatus(principal);
   }

   @PostMapping("/my/shop/relocation")
   @Operation(summary = "Переезд в другой контейнер (владелец)",
         description = "До проверки нового места магазин остаётся на старом; товары и отзывы переезжают вместе с ним")
   public VerificationDto relocate(@AuthenticationPrincipal AuthPrincipal principal,
                                   @Valid @RequestBody ShopRequests.Relocate request) {
      verification.relocate(principal.userId(), request.containerId());
      return verificationStatus(principal);
   }

   @DeleteMapping("/my/shop/relocation")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Отменить переезд")
   public void cancelRelocation(@AuthenticationPrincipal AuthPrincipal principal) {
      verification.cancelRelocation(principal.userId());
   }

   // ─────────────────────── сотрудники ───────────────────────

   @GetMapping("/my/shop/members")
   @Operation(summary = "Продавцы в боксе (21)")
   public List<MemberDto> members(@AuthenticationPrincipal AuthPrincipal principal) {
      return shops.members(principal.userId());
   }

   @PostMapping("/my/shop/members")
   @Operation(summary = "Пригласить продавца по номеру (владелец)", description = "До 5 сотрудников; приглашение уходит SMS")
   public List<MemberDto> addMember(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Valid @RequestBody ShopRequests.AddMember request) {
      return shops.addMember(principal.userId(), request.phone());
   }

   @DeleteMapping("/my/shop/members/{userId}")
   @Operation(summary = "Убрать продавца из бокса (владелец)")
   public List<MemberDto> removeMember(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long userId) {
      return shops.removeMember(principal.userId(), userId);
   }
}
