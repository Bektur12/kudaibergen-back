package kg.kudaibergen.admin.exports;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import kg.kudaibergen.admin.access.Phones;
import kg.kudaibergen.admin.audit.AuditLog;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.common.Xlsx;
import kg.kudaibergen.admin.dashboard.DashboardDtos.ChartRange;
import kg.kudaibergen.admin.dashboard.DashboardDtos.DashboardPeriod;
import kg.kudaibergen.admin.dashboard.DashboardDtos.KpiDto;
import kg.kudaibergen.admin.dashboard.DashboardService;
import kg.kudaibergen.admin.masters.AdminMasterDtos.AdminMasterRow;
import kg.kudaibergen.admin.masters.AdminMasterService;
import kg.kudaibergen.admin.masters.MasterTab;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorPeriod;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorRow;
import kg.kudaibergen.admin.requests.AdminRequestDtos.MonitorStatus;
import kg.kudaibergen.admin.requests.AdminRequestsService;
import kg.kudaibergen.admin.shops.AdminShopDtos.AdminShopRow;
import kg.kudaibergen.admin.shops.AdminShopService;
import kg.kudaibergen.admin.shops.ShopTab;
import kg.kudaibergen.common.security.CurrentUser;
import org.springframework.stereotype.Service;

/**
 * Выгрузки Excel: те же списки, что на экранах, с текущими фильтрами, все страницы (до {@link #MAX_ROWS} строк).
 * Телефоны — с учётом PII_VIEW. Каждая выгрузка пишется в журнал (EXPORT_XLSX): там персональные данные.
 */
@Service
public class AdminExportService {

   static final int MAX_ROWS = 10_000;
   static final int PAGE = 100;

   private final DashboardService dashboard;
   private final AdminShopService shops;
   private final AdminMasterService masters;
   private final AdminRequestsService requests;
   private final AuditLog audit;

   public AdminExportService(DashboardService dashboard, AdminShopService shops, AdminMasterService masters,
                             AdminRequestsService requests, AuditLog audit) {
      this.dashboard = dashboard;
      this.shops = shops;
      this.masters = masters;
      this.requests = requests;
      this.audit = audit;
   }

   public Xlsx dashboard() {
      KpiDto kpi = dashboard.kpi();
      Xlsx xlsx = new Xlsx().sheet("Сводка", List.of("Показатель", "Значение", "Изменение"), List.of(
            row("Запросов сегодня", kpi.requestsToday(), pct(kpi.requestsDeltaPct(), "% к среднему за неделю")),
            row("«Есть» за 30 минут, %", kpi.haveIn30Pct(), kpi.haveIn30DeltaPp() == null ? null
                  : signed(kpi.haveIn30DeltaPp()) + " п.п. к прошлой неделе"),
            row("Активных боксов", kpi.activeShops(), "+" + kpi.activeShopsNewWeek() + " за неделю"),
            row("Заявок на услуги сегодня", kpi.serviceRequestsToday(), pct(kpi.serviceDeltaPct(), "% к вчера"))));
      xlsx.sheet("По дням", List.of("Дата", "Запросов", "С «Есть»", "Заявок на услуги"),
            dashboard.requestsByDay(ChartRange.D30).days().stream()
                  .map(d -> List.<Object>of(d.date().toString(), d.requests(), d.withHave(), d.serviceRequests()))
                  .toList());
      xlsx.sheet("Без ответа по маркам", List.of("Марка", "Запросов", "Без ответа за 30 мин", "%", "Боксов с маркой"),
            dashboard.unansweredByBrand(DashboardPeriod.MONTH).brands().stream()
                  .map(b -> List.<Object>of(b.brand(), b.requests(), b.unanswered(), b.pct(), b.sellers())).toList());
      xlsx.sheet("Чаще всего ищут", List.of("Что", "Запросов", "% «Есть»"),
            dashboard.topSearched(DashboardPeriod.MONTH).items().stream()
                  .map(i -> List.<Object>of(i.label(), i.requests(), i.havePct())).toList());
      log("DASHBOARD", 0);
      return xlsx;
   }

   public Xlsx shops(ShopTab tab, String q) {
      List<AdminShopRow> rows = all(cursor -> shops.list(tab, q, cursor, PAGE));
      log("SHOPS", rows.size());
      return new Xlsx().sheet("Продавцы", List.of("ID", "Магазин", "Телефон владельца", "Место", "Переезд",
                  "Сверка с арендатором", "Статус", "Предупреждён", "Споров", "Подал", "Создан"),
            rows.stream().map(r -> row(r.id(), r.name(), Phones.forViewer(r.ownerPhone()), place(r.location()),
                  r.pendingLocation() == null ? null : place(r.pendingLocation()), r.tenantMatch().name(),
                  r.status().name(), r.warned(), r.openDisputes(), r.submittedAt(), r.createdAt())).toList());
   }

   public Xlsx masters(MasterTab tab, String q, String service, Long brandId) {
      List<AdminMasterRow> rows = all(cursor -> masters.list(tab, q, service, brandId, cursor, PAGE));
      log("MASTERS", rows.size());
      return new Xlsx().sheet("Мастера", List.of("ID", "Мастер / СТО", "Телефон владельца", "Телефон для клиентов",
                  "Услуги", "Адрес", "Выезд", "Радиус, км", "Проверка", "Статус", "Предупреждён", "Рейтинг",
                  "Отзывов", "Создан"),
            rows.stream().map(r -> row(r.id(), r.name(), Phones.forViewer(r.ownerPhone()), Phones.forViewer(r.phone()),
                  String.join(", ", r.services()), r.address(), r.mobile(), r.radiusKm(), r.check().name(),
                  r.status().name(), r.warned(), r.rating(), r.reviewsCount(), r.createdAt())).toList());
   }

   public Xlsx partRequests(MonitorPeriod period, MonitorStatus status, boolean noReplies, String q) {
      List<MonitorRow> rows = all(cursor -> requests.partRequests(period, status, noReplies, q, cursor, PAGE));
      log("PART_REQUESTS", rows.size());
      return new Xlsx().sheet("Запросы на запчасти", monitorHeaders(), rows.stream().map(this::monitor).toList());
   }

   public Xlsx serviceRequests(MonitorPeriod period, MonitorStatus status, boolean noReplies, String service,
                               String q) {
      List<MonitorRow> rows = all(cursor -> requests.serviceRequests(period, status, noReplies, service, q, cursor,
            PAGE));
      log("SERVICE_REQUESTS", rows.size());
      return new Xlsx().sheet("Заявки на услуги", monitorHeaders(), rows.stream().map(this::monitor).toList());
   }

   private static List<String> monitorHeaders() {
      return List.of("ID", "Текст", "Услуга", "Покупатель", "Телефон", "Создан", "Машина", "Получили", "Посмотрели",
            "Могут", "Статус", "Срочно");
   }

   private List<Object> monitor(MonitorRow r) {
      return row(r.id(), r.text(), r.service(), r.buyer().name(), Phones.forViewer(r.buyer().phone()), r.createdAt(),
            r.carLabel(), r.received(), r.seen(), r.positive(), r.status().name(), r.urgent());
   }

   private static <T> List<T> all(Function<String, AdminPage<T, ?>> page) {
      List<T> rows = new ArrayList<>();
      String cursor = null;
      do {
         AdminPage<T, ?> next = page.apply(cursor);
         rows.addAll(next.items());
         cursor = next.nextCursor();
      } while (cursor != null && rows.size() < MAX_ROWS);
      return rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows;
   }

   private void log(String what, int rows) {
      audit.record(CurrentUser.idOrNull(), "EXPORT_XLSX", "EXPORT", null, null, java.util.Map.of("rows", rows),
            what);
   }

   private static String place(kg.kudaibergen.market.dto.LocationDto location) {
      return location.rowLabel() + " · " + location.number();
   }

   private static List<Object> row(Object... values) {
      return java.util.Arrays.asList(values);
   }

   private static String pct(Integer value, String suffix) {
      return value == null ? null : signed(value) + suffix;
   }

   private static String signed(int value) {
      return (value > 0 ? "+" : "") + value;
   }
}
