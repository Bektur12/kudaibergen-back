package kg.kudaibergen.master;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.master.dto.MasterStatsDto;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.master.entity.ServiceType;
import kg.kudaibergen.stats.StatsPeriod;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Статистика мастера за неделю или месяц (вкладка «Статистика»): считается из рабочих таблиц, как у бокса.
 * «Пропущено» — время заявки вышло или её закрыли, а мастер не ответил.
 */
@Service
public class MasterStatsService {

   static final int TOP_SERVICES = 4;

   private final NamedParameterJdbcTemplate jdbc;
   private final MasterService masters;
   private final ServiceCatalog catalog;
   private final Clock clock;

   public MasterStatsService(NamedParameterJdbcTemplate jdbc, MasterService masters, ServiceCatalog catalog, Clock clock) {
      this.jdbc = jdbc;
      this.masters = masters;
      this.catalog = catalog;
      this.clock = clock;
   }

   @Transactional(readOnly = true)
   public MasterStatsDto forOwner(Long userId, StatsPeriod period, Lang lang) {
      Master master = masters.requireMine(userId);
      Instant to = clock.instant();
      Instant from = to.minus(period.length());
      Map<String, Object> params = Map.of("masterId", master.getId(), "since", Timestamp.from(from));

      Map<String, Object> counts = jdbc.queryForMap("""
            select count(*) as received,
                   count(*) filter (where sr.status = 'CAN_HELP') as can_help,
                   count(*) filter (where sr.status = 'NOT_MINE') as not_mine,
                   count(*) filter (where sr.replied_at is null and r.status <> 'ACTIVE') as missed,
                   (avg(extract(epoch from sr.replied_at - sr.notified_at))
                       filter (where sr.replied_at is not null))::float8 as avg_reply_sec
            from service_recipients sr join service_requests r on r.id = sr.request_id
            where sr.master_id = :masterId and sr.notified_at >= :since""", params);
      Integer wrote = jdbc.queryForObject("""
            select count(*) from chats where master_id = :masterId and buyer_first_message_at >= :since""",
            params, Integer.class);
      Integer deals = jdbc.queryForObject("""
            select count(*) from service_requests where closed_with_master_id = :masterId and closed_at >= :since""",
            params, Integer.class);
      Map<String, ServiceType> types = catalog.byCode();
      List<MasterStatsDto.ServiceCount> top = jdbc.query("""
                  select r.service, count(*) as requests from service_recipients sr
                  join service_requests r on r.id = sr.request_id
                  where sr.master_id = :masterId and sr.notified_at >= :since
                  group by r.service order by requests desc, r.service limit :limit""",
            Map.of("masterId", master.getId(), "since", Timestamp.from(from), "limit", TOP_SERVICES),
            (rs, i) -> {
               String code = rs.getString("service");
               ServiceType type = types.get(code);
               return new MasterStatsDto.ServiceCount(code, type == null ? code : type.name(lang), rs.getInt("requests"));
            });
      Number avg = (Number) counts.get("avg_reply_sec");
      return new MasterStatsDto(period, from, to, number(counts.get("received")), number(counts.get("can_help")),
            number(counts.get("not_mine")), wrote == null ? 0 : wrote, deals == null ? 0 : deals,
            number(counts.get("missed")), avg == null ? null : (int) Math.max(1, Math.round(avg.doubleValue() / 60)),
            top);
   }

   private static int number(Object value) {
      return value == null ? 0 : ((Number) value).intValue();
   }
}
