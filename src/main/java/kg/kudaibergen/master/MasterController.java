package kg.kudaibergen.master;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.master.dto.MasterInputs;
import kg.kudaibergen.master.dto.MasterPublicDto;
import kg.kudaibergen.master.dto.MasterReviewDto;
import kg.kudaibergen.master.dto.MyMasterDto;
import kg.kudaibergen.master.dto.ServiceTypeDto;
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

/** Мастера и СТО: справочник услуг (33, 38), свой профиль (38), профиль для клиента. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Мастера")
public class MasterController {

   private final MasterService masters;
   private final ServiceCatalog catalog;

   public MasterController(MasterService masters, ServiceCatalog catalog) {
      this.masters = masters;
      this.catalog = catalog;
   }

   @GetMapping("/service-types")
   @Operation(summary = "Услуги — плитки (33, 38)", description = """
         В порядке макета. needsLocation — заявке нужна точка на карте (эвакуатор, выездной мастер);
         urgent — «Срочно»; defaultDuration — сколько по умолчанию ждать отклики.""")
   public List<ServiceTypeDto> serviceTypes(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
         required = false) String language) {
      return catalog.list(Langs.fromHeader(language));
   }

   @GetMapping("/my/master")
   @Operation(summary = "Мой профиль мастера (38)")
   @ApiResponse(responseCode = "404", description = "NO_MASTER — ещё не зарегистрирован, вести на регистрацию")
   public MyMasterDto mine(@AuthenticationPrincipal AuthPrincipal principal, @Parameter(hidden = true)
   @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return masters.mine(principal.userId(), Langs.fromHeader(language));
   }

   @PostMapping("/my/master")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Стать мастером (38)", description = """
         Что делаю (services ≥ 1), марки (allBrands — все), какие машины (origins пусто — любые), адрес и точка,
         радиус 1–30 км (по умолчанию 5), выезжаю ли, часы. Пользователь переходит в режим MASTER.""")
   @ApiResponse(responseCode = "409", description = "ALREADY_MASTER")
   public MyMasterDto create(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody MasterInputs.CreateMaster request,
                             @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                   required = false) String language) {
      return masters.create(principal.userId(), request, Langs.fromHeader(language));
   }

   @PatchMapping("/my/master")
   @Operation(summary = "Изменить профиль мастера (38)", description = "null — поле не меняется")
   public MyMasterDto update(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody MasterInputs.UpdateMaster request,
                             @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                   required = false) String language) {
      return masters.update(principal.userId(), request, Langs.fromHeader(language));
   }

   @PatchMapping("/my/master/accepting")
   @Operation(summary = "«● Принимаю» (39)", description = "Выключено — заявки не приходят")
   public MyMasterDto accepting(@AuthenticationPrincipal AuthPrincipal principal,
                                @Valid @RequestBody MasterInputs.SetAccepting request,
                                @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                      required = false) String language) {
      return masters.setAccepting(principal.userId(), request.accepting(), Langs.fromHeader(language));
   }

   @PutMapping("/my/master/avatar")
   @Operation(summary = "Аватар мастера", description = "mediaId из POST /media/photos (purpose=MASTER или AVATAR)")
   public MyMasterDto avatar(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody MasterInputs.Avatar request,
                             @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                   required = false) String language) {
      return masters.setAvatar(principal.userId(), request.mediaId(), Langs.fromHeader(language));
   }

   @DeleteMapping("/my/master/avatar")
   @Operation(summary = "Убрать аватар мастера")
   public MyMasterDto removeAvatar(@AuthenticationPrincipal AuthPrincipal principal, @Parameter(hidden = true)
   @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return masters.setAvatar(principal.userId(), null, Langs.fromHeader(language));
   }

   @PutMapping("/my/master/photos")
   @Operation(summary = "Фото работ и места (38)", description = "Весь список по порядку, до 8, первое — обложка (purpose=MASTER)")
   public MyMasterDto photos(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody MasterInputs.Photos request,
                             @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                   required = false) String language) {
      return masters.setPhotos(principal.userId(), request.mediaIds(), Langs.fromHeader(language));
   }

   @GetMapping("/masters/{id}")
   @Operation(summary = "Профиль мастера для клиента")
   public MasterPublicDto profile(@PathVariable Long id, @Parameter(hidden = true)
   @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return masters.publicProfile(id, Langs.fromHeader(language));
   }

   @GetMapping("/masters/public/{publicId}")
   @Operation(summary = "Профиль мастера по ссылке «Поделиться»")
   public MasterPublicDto profileByPublicId(@PathVariable String publicId, @Parameter(hidden = true)
   @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return masters.publicProfileByPublicId(publicId, Langs.fromHeader(language));
   }

   @GetMapping("/masters/{id}/reviews")
   @Operation(summary = "Отзывы о мастере", description = "Последние 50, новые сверху")
   public List<MasterReviewDto> reviews(@PathVariable Long id, @Parameter(hidden = true)
   @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return masters.reviews(id, Langs.fromHeader(language));
   }
}
