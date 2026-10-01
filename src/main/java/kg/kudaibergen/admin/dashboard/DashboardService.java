package kg.kudaibergen.admin.dashboard;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import kg.kudaibergen.admin.dashboard.DashboardDtos.BrandHint;
import kg.kudaibergen.admin.dashboard.DashboardDtos.BrandUnanswered;
import kg.kudaibergen.admin.dashboard.DashboardDtos.ChartRange;
import kg.kudaibergen.admin.dashboard.DashboardDtos.DashboardPeriod;
import kg.kudaibergen.admin.dashboard.DashboardDtos.DayPoint;
import kg.kudaibergen.admin.dashboard.DashboardDtos.KpiDto;
import kg.kudaibergen.admin.dashboard.DashboardDtos.PendingDto;
import kg.kudaibergen.admin.dashboard.DashboardDtos.PendingItem;
import kg.kudaibergen.admin.moderation.ComplaintLabels;
import kg.kudaibergen.admin.dashboard.DashboardDtos.RequestsByDayDto;
import kg.kudaibergen.admin.dashboard.DashboardDtos.SearchedItem;
import kg.kudaibergen.admin.dashboard.DashboardDtos.TopSearchedDto;
import kg.kudaibergen.admin.dashboard.DashboardDtos.UnansweredByBrandDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сводка [A1]. Агрегаты тяжёлые, поэтому кэшируются на {@link #TTL}; в ответе generatedAt — когда посчитано.
 * Дни — по Бишкеку. «Есть за 30 минут» — первый ответ «Есть» не позже 30 минут после создания запроса.
 */
@Service
public class DashboardService {

   static final Duration TTL = Duration.ofSeconds(90);
   static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");
   static final int MIN_BRAND_REQUESTS = 3;
   static final int HINT_PCT = 20;

   private final NamedParameterJdbcTemplate jdbc;
   private final Clock clock;
   private final Map<String, CachedAggregate> cache = new ConcurrentHashMap<>();

   private record CachedAggregate(Object value, Instant at) {
   }

   public DashboardService(NamedParameterJdbcTemplate jdbc, Clock clock) {
      this.jdbc = jdbc;
      this.clock = clock;
   }

   // ─────────────────────── KPI ───────────────────────

   @Transactional(readOnly = true)
   public KpiDto kpi() {
      return cached("kpi", () -> {
         Instant now = clock.instant();
         Instant today = LocalDate.now(BISHKEK).atStartOfDay(BISHKEK).toInstant();
         MapSqlParameterSource p = new MapSqlParameterSource()
               .addValue("today", ts(today))
               .addValue("weekAgo", ts(today.minus(Duration.ofDays(7))))
               .addValue("yesterday", ts(today.minus(Duration.ofDays(1))))
               .addValue("now", ts(now))
               .addValue("w1from", ts(now.minus(Duration.ofDays(7))))
               .addValue("w1to", ts(now.minus(Duration.ofMinutes(30))))
               .addValue("w2from", ts(now.minus(Duration.ofDays(14))))
               .addValue("w2to", ts(now.minus(Duration.ofDays(7)).minus(Duration.ofMinutes(30))));
         return jdbc.queryForObject("""
               select (select count(*) from part_requests where created_at >= :today) as today,
                      (select count(*) from part_requests where created_at >= :weekAgo and created_at < :today) as prev7,
                      (select count(*) filter (where """ + HAVE_IN_30 + """
                       ) * 100.0 / nullif(count(*), 0) from part_requests r
                       where r.created_at >= :w1from and r.created_at < :w1to) as have_now,
                      (select count(*) filter (where """ + HAVE_IN_30 + """
                       ) * 100.0 / nullif(count(*), 0) from part_requests r
                       where r.created_at >= :w2from and r.created_at < :w2to) as have_prev,
                      (select count(*) from shops where status = 'ACTIVE') as shops,
                      (select count(*) from shops where status = 'ACTIVE'
                         and coalesce(verified_at, created_at) >= :w1from) as shops_new,
                      (select count(*) from service_requests where created_at >= :today) as services,
                      (select count(*) from service_requests where created_at >= :yesterday and created_at < :today)
                         as services_yesterday""", p, (rs, n) -> {
            long requestsToday = rs.getLong("today");
            double avg = rs.getLong("prev7") / 7.0;
            Double haveNow = rs.getBigDecimal("have_now") == null ? null : rs.getBigDecimal("have_now").doubleValue();
            Double havePrev = rs.getBigDecimal("have_prev") == null ? null : rs.getBigDecimal("have_prev").doubleValue();
            long services = rs.getLong("services");
            long servicesYesterday = rs.getLong("services_yesterday");
            return new KpiDto(requestsToday, deltaPct(requestsToday, avg), round(haveNow),
                  haveNow == null || havePrev == null ? null : (int) Math.round(haveNow - havePrev),
                  rs.getLong("shops"), rs.getLong("shops_new"), services, deltaPct(services, servicesYesterday),
                  now);
         });
      });
   }

   /** Первый «Есть» не позже 30 минут после создания запроса. */
   static final String HAVE_IN_30 = " exists (select 1 from request_replies rp where rp.request_id = r.id"
         + " and rp.answer = 'HAVE' and rp.created_at <= r.created_at + interval '30 minutes') ";

   // ─────────────────────── график ───────────────────────

   @Transactional(readOnly = true)
   public RequestsByDayDto requestsByDay(ChartRange range) {
      return cached("days:" + range, () -> {
         int days = switch (range) {
            case D14 -> 14;
            case D30 -> 30;
            case QUARTER -> 90;
         };
         LocalDate first = LocalDate.now(BISHKEK).minusDays(days - 1L);
         Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
         for (int i = 0; i < days; i++) {
            byDay.put(first.plusDays(i), new long[3]);
         }
         MapSqlParameterSource p = new MapSqlParameterSource("from", ts(first.atStartOfDay(BISHKEK).toInstant()));
         jdbc.query("""
               select (r.created_at at time zone 'Asia/Bishkek')::date as day, count(*) as requests,
                      count(*) filter (where r.have_count > 0) as with_have
               from part_requests r where r.created_at >= :from group by 1""", p, rs -> {
            long[] row = byDay.get(rs.getDate("day").toLocalDate());
            if (row != null) {
               row[0] = rs.getLong("requests");
               row[1] = rs.getLong("with_have");
            }
         });
         jdbc.query("""
               select (created_at at time zone 'Asia/Bishkek')::date as day, count(*) as services
               from service_requests where created_at >= :from group by 1""", p, rs -> {
            long[] row = byDay.get(rs.getDate("day").toLocalDate());
            if (row != null) {
               row[2] = rs.getLong("services");
            }
         });
         List<DayPoint> points = byDay.entrySet().stream()
               .map(e -> new DayPoint(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
         return new RequestsByDayDto(range, points, clock.instant());
      });
   }

   // ─────────────────────── без ответа по маркам ───────────────────────

   @Transactional(readOnly = true)
   public UnansweredByBrandDto unansweredByBrand(DashboardPeriod period) {
      return cached("brands:" + period, () -> {
         Instant now = clock.instant();
         MapSqlParameterSource p = new MapSqlParameterSource()
               .addValue("from", ts(now.minus(period == DashboardPeriod.WEEK ? Duration.ofDays(7) : Duration.ofDays(30))))
               .addValue("to", ts(now.minus(Duration.ofMinutes(30))))
               .addValue("min", MIN_BRAND_REQUESTS);
         List<BrandUnanswered> brands = jdbc.query("""
               select b.id, b.name, count(*) as requests,
                      count(*) filter (where not """ + HAVE_IN_30 + """
                      ) as unanswered,
                      (select count(*) from shop_brands sb join shops s on s.id = sb.shop_id
                        where sb.brand_id = b.id and s.status = 'ACTIVE') as sellers
                 from part_requests r join brands b on b.id = r.brand_id
                where r.created_at >= :from and r.created_at < :to and not r.hidden_by_admin
                group by b.id, b.name
               having count(*) >= :min""", p, (rs, n) -> {
            long requests = rs.getLong("requests");
            long unanswered = rs.getLong("unanswered");
            return new BrandUnanswered(rs.getLong("id"), rs.getString("name"), requests, unanswered,
                  (int) Math.round(unanswered * 100.0 / requests), rs.getLong("sellers"));
         });
         List<BrandUnanswered> sorted = new ArrayList<>(brands);
         sorted.sort(Comparator.comparingInt(BrandUnanswered::pct).reversed()
               .thenComparing(Comparator.comparingLong(BrandUnanswered::requests).reversed()));
         return new UnansweredByBrandDto(period, sorted, hint(sorted), now);
      });
   }

   static BrandHint hint(List<BrandUnanswered> sorted) {
      if (sorted.isEmpty() || sorted.get(0).pct() < HINT_PCT) {
         return null;
      }
      BrandUnanswered worst = sorted.get(0);
      String sellers = worst.sellers() == 0 ? "её не продаёт ни один бокс"
            : "её продают всего " + worst.sellers() + " " + boxes(worst.sellers());
      return new BrandHint(worst.brandId(), worst.brand() + ": " + worst.pct() + "% запросов без ответа за 30 минут — "
            + sellers + ". Напомните продавцам рассылкой", List.of(worst.brandId()));
   }

   static String boxes(long n) {
      long mod10 = n % 10;
      long mod100 = n % 100;
      if (mod10 == 1 && mod100 != 11) {
         return "бокс";
      }
      return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "бокса" : "боксов";
   }

   // ─────────────────────── чаще всего ищут ───────────────────────

   @Transactional(readOnly = true)
   public TopSearchedDto topSearched(DashboardPeriod period) {
      return cached("top:" + period, () -> {
         Instant now = clock.instant();
         MapSqlParameterSource p = new MapSqlParameterSource("from",
               ts(now.minus(period == DashboardPeriod.WEEK ? Duration.ofDays(7) : Duration.ofDays(30))));
         List<SearchedItem> items = jdbc.query("""
               select coalesce(h.text_ru, initcap(lower(regexp_replace(trim(r.text), '\\s+', ' ', 'g')))) as label,
                      count(*) as requests, count(*) filter (where r.have_count > 0) as with_have
                 from part_requests r left join part_hints h on h.id = r.hint_id
                where r.created_at >= :from and not r.hidden_by_admin
                group by 1 order by count(*) desc, 1 limit 10""", p, (rs, n) -> new SearchedItem(rs.getString("label"),
               rs.getLong("requests"), (int) Math.round(rs.getLong("with_have") * 100.0 / rs.getLong("requests"))));
         return new TopSearchedDto(period, items, now);
      });
   }

   // ─────────────────────── ждут действий ───────────────────────

   @Transactional(readOnly = true)
   public PendingDto pending() {
      return cached("pending", () -> {
         Map<String, Object> none = Map.of();
         Long shops = jdbc.queryForObject("""
               select count(*) from shops where status = 'PENDING_VERIFICATION' or pending_container_id is not null""",
               none, Long.class);
         Long masters = jdbc.queryForObject("select count(*) from masters where status = 'PENDING_VERIFICATION'", none,
               Long.class);
         Long complaints = jdbc.queryForObject(
               "select count(*) from complaints where status = 'OPEN' and type <> 'CONTAINER_CLAIM'", none, Long.class);
         Long disputes = jdbc.queryForObject("select count(*) from container_disputes where status = 'OPEN'", none,
               Long.class);
         List<PendingItem> latest = jdbc.query("""
               (select 'SHOP' as kind, s.id, s.name as title, u.phone, s.created_at from shops s
                  join users u on u.id = s.owner_id
                 where s.status = 'PENDING_VERIFICATION' or s.pending_container_id is not null
                 order by s.created_at desc limit 5)
               union all
               (select 'MASTER', m.id, m.name, u.phone, m.created_at from masters m join users u on u.id = m.owner_id
                 where m.status = 'PENDING_VERIFICATION' order by m.created_at desc limit 5)
               union all
               (select 'COMPLAINT', c.id, c.type || '|' || c.reason, null, c.created_at from complaints c
                 where c.status = 'OPEN' and c.type <> 'CONTAINER_CLAIM' order by c.created_at desc limit 5)
               union all
               (select 'DISPUTE', d.id, r.label || ' · ' || k.number, null, d.created_at from container_disputes d
                  join containers k on k.id = d.container_id join market_rows r on r.id = k.row_id
                 where d.status = 'OPEN' order by d.created_at desc limit 5)
               order by created_at desc limit 10""", none, (rs, n) -> new PendingItem(rs.getString("kind"),
               rs.getLong("id"), rs.getString("title"), rs.getString("phone"),
               rs.getTimestamp("created_at").toInstant()));
         latest = latest.stream().map(DashboardService::readable).toList();
         return new PendingDto(nz(shops), nz(masters), nz(complaints), nz(disputes), latest, clock.instant());
      });
   }

   /** У жалобы title — готовый текст «Чат · Спам / мошенничество», а не коды. */
   private static PendingItem readable(PendingItem item) {
      if (!"COMPLAINT".equals(item.kind()) || item.title() == null) {
         return item;
      }
      String[] codes = item.title().split("\\|", 2);
      return new PendingItem(item.kind(), item.id(), ComplaintLabels.title(codes[0], codes.length > 1 ? codes[1] : null),
            item.phone(), item.createdAt());
   }

   /** Сбросить кэш — после «Обновить» в интерфейсе. */
   public void refresh() {
      cache.clear();
   }

   // ─────────────────────── внутреннее ───────────────────────

   @SuppressWarnings("unchecked")
   private <T> T cached(String key, Supplier<T> loader) {
      Instant now = clock.instant();
      CachedAggregate hit = cache.get(key);
      if (hit != null && hit.at().plus(TTL).isAfter(now)) {
         return (T) hit.value();
      }
      T value = loader.get();
      cache.put(key, new CachedAggregate(value, now));
      return value;
   }

   static Integer deltaPct(long current, double base) {
      if (base <= 0) {
         return null;
      }
      return (int) Math.round((current - base) * 100.0 / base);
   }

   private static Integer round(Double value) {
      return value == null ? null : (int) Math.round(value);
   }

   private static long nz(Long value) {
      return value == null ? 0 : value;
   }

   private static Timestamp ts(Instant instant) {
      return Timestamp.from(instant);
   }
}
