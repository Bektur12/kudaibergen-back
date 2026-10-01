package kg.kudaibergen.admin.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.admin.dashboard.DashboardDtos.ChartRange;
import kg.kudaibergen.admin.dashboard.DashboardDtos.DashboardPeriod;
import kg.kudaibergen.admin.dashboard.DashboardDtos.KpiDto;
import kg.kudaibergen.admin.dashboard.DashboardDtos.PendingDto;
import kg.kudaibergen.admin.dashboard.DashboardDtos.RequestsByDayDto;
import kg.kudaibergen.admin.dashboard.DashboardDtos.TopSearchedDto;
import kg.kudaibergen.admin.dashboard.DashboardDtos.UnansweredByBrandDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Сводка [A1]. Агрегаты кэшируются на 90 с, generatedAt — когда посчитаны. */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "Админка: сводка")
public class DashboardController {

   private final DashboardService dashboard;

   public DashboardController(DashboardService dashboard) {
      this.dashboard = dashboard;
   }

   @GetMapping("/kpi")
   @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
   @Operation(summary = "KPI: запросов сегодня, «Есть» за 30 минут, активных боксов, заявок на услуги")
   public KpiDto kpi() {
      return dashboard.kpi();
   }

   @GetMapping("/requests-by-day")
   @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
   @Operation(summary = "График по дням (Бишкек): запросы, с «Есть», заявки на услуги")
   public RequestsByDayDto requestsByDay(@RequestParam(defaultValue = "D14") ChartRange range) {
      return dashboard.requestsByDay(range);
   }

   @GetMapping("/unanswered-by-brand")
   @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
   @Operation(summary = "Без ответа за 30 минут по маркам", description = "От 3 запросов за период. hint — текст для "
         + "худшей марки и фильтр рассылки «продают эту марку» (broadcastBrandIds)")
   public UnansweredByBrandDto unansweredByBrand(@RequestParam(defaultValue = "WEEK") DashboardPeriod period) {
      return dashboard.unansweredByBrand(period);
   }

   @GetMapping("/top-searched")
   @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
   @Operation(summary = "Чаще всего ищут", description = "По подсказке «Что ищем?», иначе по тексту запроса; % «Есть»")
   public TopSearchedDto topSearched(@RequestParam(defaultValue = "WEEK") DashboardPeriod period) {
      return dashboard.topSearched(period);
   }

   @GetMapping("/pending")
   @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
   @Operation(summary = "Ждут действий: продавцы и мастера на проверке, жалобы, споры", description = "latest — 10 свежих")
   public PendingDto pending() {
      return dashboard.pending();
   }

   @PostMapping("/refresh")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
   @kg.kudaibergen.admin.audit.Audited(action = "DASHBOARD_REFRESH", entity = "DASHBOARD")
   @Operation(summary = "Пересчитать сводку сейчас", description = "Сбрасывает кэш агрегатов")
   public void refresh() {
      dashboard.refresh();
   }
}
