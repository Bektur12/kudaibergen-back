package kg.kudaibergen.admin.exports;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.admin.masters.MasterTab;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorPeriod;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorStatus;
import kg.kudaibergen.admin.shops.ShopTab;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Выгрузки Excel с текущими фильтрами экрана. Нужны право EXPORT_EXCEL и право смотреть сам раздел. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Админка: выгрузки")
public class AdminExportController {

   private final AdminExportService exports;

   public AdminExportController(AdminExportService exports) {
      this.exports = exports;
   }

   @GetMapping(value = "/dashboard/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
   @PreAuthorize("hasAuthority('EXPORT_EXCEL') and hasAuthority('DASHBOARD_VIEW')")
   @Operation(summary = "Сводка в Excel: KPI, по дням за 30 дней, без ответа по маркам, чаще всего ищут")
   public ResponseEntity<byte[]> dashboard() {
      return exports.dashboard().response("svodka");
   }

   @GetMapping(value = "/shops/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
   @PreAuthorize("hasAuthority('EXPORT_EXCEL') and hasAuthority('SELLERS_VIEW')")
   @Operation(summary = "Продавцы в Excel", description = "Те же tab и q, что у списка; до 10 000 строк")
   public ResponseEntity<byte[]> shops(@RequestParam(defaultValue = "ALL") ShopTab tab,
                                       @RequestParam(required = false) String q) {
      return exports.shops(tab, q).response("prodavcy");
   }

   @GetMapping(value = "/masters/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
   @PreAuthorize("hasAuthority('EXPORT_EXCEL') and hasAuthority('MASTERS_VIEW')")
   @Operation(summary = "Мастера в Excel", description = "Те же фильтры, что у списка")
   public ResponseEntity<byte[]> masters(@RequestParam(defaultValue = "ALL") MasterTab tab,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String service,
                                         @RequestParam(required = false) Long brandId) {
      return exports.masters(tab, q, service, brandId).response("mastera");
   }

   @GetMapping(value = "/part-requests/export.xlsx",
         produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
   @PreAuthorize("hasAuthority('EXPORT_EXCEL') and hasAuthority('REQUESTS_VIEW')")
   @Operation(summary = "Запросы на запчасти в Excel")
   public ResponseEntity<byte[]> partRequests(@RequestParam(defaultValue = "TODAY") MonitorPeriod period,
                                              @RequestParam(required = false) MonitorStatus status,
                                              @RequestParam(defaultValue = "false") boolean noReplies,
                                              @RequestParam(required = false) String q) {
      return exports.partRequests(period, status, noReplies, q).response("zaprosy");
   }

   @GetMapping(value = "/service-requests/export.xlsx",
         produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
   @PreAuthorize("hasAuthority('EXPORT_EXCEL') and hasAuthority('REQUESTS_VIEW')")
   @Operation(summary = "Заявки на услуги в Excel")
   public ResponseEntity<byte[]> serviceRequests(@RequestParam(defaultValue = "TODAY") MonitorPeriod period,
                                                 @RequestParam(required = false) MonitorStatus status,
                                                 @RequestParam(defaultValue = "false") boolean noReplies,
                                                 @RequestParam(required = false) String service,
                                                 @RequestParam(required = false) String q) {
      return exports.serviceRequests(period, status, noReplies, service, q).response("zayavki");
   }
}
