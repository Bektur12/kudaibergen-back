package kg.kudaibergen.master;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.idempotency.Idempotent;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.master.dto.ServiceEstimateDto;
import kg.kudaibergen.master.dto.ServiceInputs;
import kg.kudaibergen.master.dto.ServiceOfferDto;
import kg.kudaibergen.master.dto.ServiceRequestDetailDto;
import kg.kudaibergen.master.dto.ServiceRequestSummaryDto;
import kg.kudaibergen.master.dto.ServiceStatsDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Заявки на услуги — сторона клиента (экраны 05б, 33–37). */
@RestController
@RequestMapping("/api/v1/service-requests")
@Tag(name = "Заявки на услуги: клиент")
public class ServiceRequestController {

   private final ServiceRequestService requests;

   public ServiceRequestController(ServiceRequestService requests) {
      this.requests = requests;
   }

   @GetMapping("/estimate")
   @Operation(summary = "Сколько мастеров получат заявку (36)", description = """
         «Заявку получат 12 мастеров». Машина — carId из гаража или brandId (+ origin); lat/lng — точка клиента,
         radiusKm — по умолчанию 5.""")
   public ServiceEstimateDto estimate(@AuthenticationPrincipal AuthPrincipal principal,
                                      @RequestParam String service,
                                      @RequestParam(required = false) Long carId,
                                      @RequestParam(required = false) Long brandId,
                                      @RequestParam(required = false) CarOrigin origin,
                                      @RequestParam double lat, @RequestParam double lng,
                                      @RequestParam(required = false) Integer radiusKm,
                                      @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                            required = false) String language) {
      return requests.estimate(principal.userId(), service, carId, brandId, origin, lat, lng, radiusKm,
            Langs.fromHeader(language));
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @Idempotent
   @Operation(summary = "Отправить заявку (36)", description = """
         Уходит мастерам, которые делают эту услугу, работают с маркой и страной машины, принимают сейчас и
         находятся в радиусе. Им — пуш. Срок по умолчанию — из справочника (эвакуатор — 15 минут, «Срочно»).""")
   @ApiResponse(responseCode = "409", description = "NO_RECIPIENTS — рядом некому, OPEN_REQUESTS_LIMIT")
   public ServiceRequestDetailDto create(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody ServiceInputs.CreateServiceRequest request,
                                         @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                               required = false) String language) {
      return requests.create(principal.userId(), request, Langs.fromHeader(language));
   }

   @GetMapping("/my")
   @Operation(summary = "Мои заявки (05б)", description = "Активные сверху, истёкшие и закрытые ниже")
   public CursorPage<ServiceRequestSummaryDto> mine(@AuthenticationPrincipal AuthPrincipal principal,
                                                    @RequestParam(required = false) String cursor,
                                                    @RequestParam(required = false) Integer limit,
                                                    @Parameter(hidden = true) @RequestHeader(
                                                          value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return requests.mine(principal.userId(), cursor, limit, Langs.fromHeader(language));
   }

   @GetMapping("/{id}")
   @Operation(summary = "Заявка (37)", description = """
         state: WAITING, HAS_OFFERS — активна; NO_OFFERS — время вышло без откликов (предложить расширить радиус);
         EXPIRED — время вышло, отклики есть; CLOSED""")
   public ServiceRequestDetailDto detail(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                         @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                               required = false) String language) {
      return requests.detail(principal, id, Langs.fromHeader(language));
   }

   @GetMapping("/{id}/offers")
   @Operation(summary = "Кто может помочь (37)", description = """
         Отклики «Могу помочь» по времени: мастер, расстояние, цена «от», когда может, сообщение, chatId.
         afterId — только новые.""")
   public List<ServiceOfferDto> offers(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                       @RequestParam(required = false) Long afterId) {
      return requests.offers(principal, id, afterId);
   }

   @GetMapping("/{id}/stats")
   @Operation(summary = "Статистика заявки (37)", description = """
         Получили / Посмотрели / Могут / Не их профиль / Молчат, таймер и радиус. Живое обновление —
         событие SERVICE_REQUEST_STATS в канале inbox клиента.""")
   public ServiceStatsDto stats(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return requests.stats(principal, id);
   }

   @PostMapping("/{id}/extend")
   @Operation(summary = "Продлить (37)", description = "minutes: 30, 60 или 180, не больше 3 раз")
   @ApiResponse(responseCode = "409", description = "EXTEND_LIMIT, REQUEST_CLOSED")
   public ServiceRequestDetailDto extend(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                         @Valid @RequestBody ServiceInputs.ServiceExtend request,
                                         @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                               required = false) String language) {
      return requests.extend(principal, id, request, Langs.fromHeader(language));
   }

   @PostMapping("/{id}/widen")
   @Operation(summary = "Расширить радиус (+5 км)", description = "До 50 км; новым мастерам — пуш, срок заново")
   @ApiResponse(responseCode = "409", description = "RADIUS_LIMIT, REQUEST_CLOSED")
   public ServiceRequestDetailDto widen(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                        @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                              required = false) String language) {
      return requests.widen(principal, id, Langs.fromHeader(language));
   }

   @PostMapping("/{id}/close")
   @Operation(summary = "Договорились / закрыть (37)", description = """
         masterId — с кем договорились (только откликнувшийся «Могу помочь»), stars 1–5 и tags — оценка ему.
         Без masterId — просто закрыть.""")
   public ServiceRequestDetailDto close(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                        @Valid @RequestBody ServiceInputs.Close request,
                                        @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                              required = false) String language) {
      return requests.close(principal, id, request, Langs.fromHeader(language));
   }
}
