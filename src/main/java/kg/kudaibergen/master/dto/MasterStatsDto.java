package kg.kudaibergen.master.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.stats.StatsPeriod;
import org.springframework.lang.Nullable;

/**
 * Статистика мастера за неделю или месяц: сколько заявок пришло, «Могу помочь» / «Не моё», написали в чат,
 * договорились, пропущено, среднее время отклика, какие услуги спрашивали чаще.
 */
public record MasterStatsDto(StatsPeriod period, Instant from, Instant to, int received, int canHelp, int notMine,
                             int wroteInChat, int deals, int unanswered, @Nullable Integer avgReplyMinutes,
                             List<ServiceCount> topServices) {

   public record ServiceCount(String code, String name, int count) {
   }
}
