package kg.kudaibergen.admin.requests;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.requests.AdminRequestDtos.AdminPartRequestDto;
import kg.kudaibergen.admin.requests.AdminRequestDtos.AdminRealtimeDto;
import kg.kudaibergen.admin.requests.AdminRequestDtos.AdminServiceRequestDto;
import kg.kudaibergen.admin.requests.AdminRequestDtos.HideRequestBody;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorCounts;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorPeriod;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorRow;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorStatus;
import kg.kudaibergen.chat.realtime.CentrifugoTokens;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Запросы на запчасти и заявки на услуги: мониторинг [A8]. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Админка: запросы и заявки")
public class AdminRequestsController {

   private final AdminRequestsService requests;
   private final CentrifugoTokens tokens;

   public AdminRequestsController(AdminRequestsService requests, CentrifugoTokens tokens) {
      this.requests = requests;
      this.tokens = tokens;
   }

   @GetMapping("/part-requests")
   @PreAuthorize("hasAuthority('REQUESTS_VIEW')")
   @Operation(summary = "Запросы на запчасти", description = "period — по дате создания (Бишкек); noReplies — ни одного "
         + "«Есть»; q — текст или цифры телефона. counts — оба таба и «Без откликов» за период")
   public AdminPage<MonitorRow, MonitorCounts> partRequests(
         @RequestParam(defaultValue = "TODAY") MonitorPeriod period,
         @RequestParam(required = false) MonitorStatus status,
         @RequestParam(defaultValue = "false") boolean noReplies,
         @RequestParam(required = false) String q,
         @RequestParam(required = false) String cursor,
         @RequestParam(required = false) Integer limit) {
      return requests.partRequests(period, status, noReplies, q, cursor, limit);
   }

   @GetMapping("/service-requests")
   @PreAuthorize("hasAuthority('REQUESTS_VIEW')")
   @Operation(summary = "Заявки на услуги", description = "service — код услуги; остальное — как у запросов")
   public AdminPage<MonitorRow, MonitorCounts> serviceRequests(
         @RequestParam(defaultValue = "TODAY") MonitorPeriod period,
         @RequestParam(required = false) MonitorStatus status,
         @RequestParam(defaultValue = "false") boolean noReplies,
         @RequestParam(required = false) String service,
         @RequestParam(required = false) String q,
         @RequestParam(required = false) String cursor,
         @RequestParam(required = false) Integer limit) {
      return requests.serviceRequests(period, status, noReplies, service, q, cursor, limit);
   }

   @GetMapping("/part-requests/{id}")
   @PreAuthorize("hasAuthority('REQUESTS_VIEW')")
   @Operation(summary = "Запрос на запчасть: машина, текст, кому ушёл, ответы продавцов")
   public AdminPartRequestDto partRequest(@PathVariable Long id) {
      return requests.partRequest(id);
   }

   @GetMapping("/service-requests/{id}")
   @PreAuthorize("hasAuthority('REQUESTS_VIEW')")
   @Operation(summary = "Заявка: машина снимком, описание и вложения, когда / где / радиус, статистика, отклики",
         description = "Отклик: мастер, через сколько ответил, когда может, цена «от»")
   public AdminServiceRequestDto serviceRequest(@PathVariable Long id) {
      return requests.serviceRequest(id);
   }

   @PostMapping("/part-requests/{id}/widen")
   @PreAuthorize("hasAuthority('REQUESTS_MANAGE')")
   @Audited(action = "PART_REQUEST_WIDEN", entity = "PART_REQUEST", id = "#id")
   @Operation(summary = "Расширить до всего рынка", description = "Как «Отправить всему рынку» у покупателя: пуш "
         + "новым продавцам, срок заново")
   @ApiResponse(responseCode = "409", description = "REQUEST_CLOSED")
   public AdminPartRequestDto widenPart(@PathVariable Long id) {
      AuditTrail.before(requests.partRequest(id));
      return requests.widenPart(id);
   }

   @PostMapping("/service-requests/{id}/widen")
   @PreAuthorize("hasAuthority('REQUESTS_MANAGE')")
   @Audited(action = "SERVICE_REQUEST_WIDEN", entity = "SERVICE_REQUEST", id = "#id")
   @Operation(summary = "Расширить радиус", description = "+5 км (до 50), пуш новым мастерам, срок заново")
   @ApiResponse(responseCode = "409", description = "RADIUS_LIMIT, REQUEST_CLOSED")
   public AdminServiceRequestDto widenService(@PathVariable Long id) {
      AuditTrail.before(requests.serviceRequest(id));
      return requests.widenService(id);
   }

   @PostMapping("/part-requests/{id}/hide")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('REQUESTS_MANAGE')")
   @Audited(action = "PART_REQUEST_HIDE", entity = "PART_REQUEST", id = "#id", comment = "#body.reason")
   @Operation(summary = "Скрыть запрос", description = "Закрывается и пропадает у продавцов; покупателю — пуш с причиной")
   public void hidePart(@PathVariable Long id, @Valid @RequestBody HideRequestBody body) {
      AuditTrail.before(requests.partRequest(id));
      requests.hide(false, id, body.reason());
   }

   @PostMapping("/service-requests/{id}/hide")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('REQUESTS_MANAGE')")
   @Audited(action = "SERVICE_REQUEST_HIDE", entity = "SERVICE_REQUEST", id = "#id", comment = "#body.reason")
   @Operation(summary = "Скрыть заявку", description = "Закрывается и пропадает у мастеров; клиенту — пуш с причиной")
   public void hideService(@PathVariable Long id, @Valid @RequestBody HideRequestBody body) {
      AuditTrail.before(requests.serviceRequest(id));
      requests.hide(true, id, body.reason());
   }

   @GetMapping("/realtime/token")
   @PreAuthorize("hasAuthority('REQUESTS_VIEW')")
   @Operation(summary = "Живой мониторинг (Centrifugo)", description = "Токен подключения и подписки на admin:requests: "
         + "события {type: REQUEST_CHANGED, payload: {kind: PART|SERVICE, id, event}}")
   public AdminRealtimeDto realtime(@AuthenticationPrincipal AuthPrincipal admin) {
      CentrifugoTokens.Token connection = tokens.connection(admin.userId());
      CentrifugoTokens.Token subscription = tokens.subscription(admin.userId(), AdminRequestsRealtime.CHANNEL);
      return new AdminRealtimeDto(connection.token(), subscription.token(), AdminRequestsRealtime.CHANNEL,
            connection.expiresInSeconds());
   }
}
