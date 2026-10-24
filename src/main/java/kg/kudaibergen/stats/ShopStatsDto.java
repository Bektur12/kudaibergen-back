package kg.kudaibergen.stats;

import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

/**
 * Статистика бокса за период (17).
 * <ul>
 *   <li>requestsByBrands — сколько запросов пришло (по маркам бокса или выбором контейнера);</li>
 *   <li>answeredHave / answeredNotHave — ответили «Есть» / «Нет»;</li>
 *   <li>wroteInChat — покупатели, написавшие в чат хотя бы одно сообщение;</li>
 *   <li>buyersArrived — нажали «Я на месте» в чате;</li>
 *   <li>sales — запросы, закрытые покупателем «Купил» у этого бокса;</li>
 *   <li>unanswered — время запроса вышло или его закрыли, а бокс так и не ответил («Смотреть» →
 *   лента с filter=UNANSWERED);</li>
 *   <li>avgReplyMinutes — среднее время от рассылки до ответа, null — ответов не было;</li>
 *   <li>topCategories — чаще всего спрашивали (до 4, только запросы с выбранной категорией);</li>
 *   <li>partViews — просмотры карточек запчастей бокса;</li>
 *   <li>answeredPercent — «92% ответили» (15): ответили «Есть» или «Нет» из пришедших, null — запросов не было;</li>
 *   <li>falseHave — «0 «не было»»: отметки «сказали «есть», а не было» за период;</li>
 *   <li>requestsPausedUntil — бокс на паузе за три такие отметки: до этого момента запросы не приходят.</li>
 * </ul>
 */
public record ShopStatsDto(StatsPeriod period, Instant from, Instant to, int requestsByBrands, int answeredHave,
                           int answeredNotHave, int wroteInChat, int buyersArrived, int sales, int unanswered,
                           @Nullable Integer avgReplyMinutes, long partViews, List<TopCategory> topCategories,
                           @Nullable Integer answeredPercent, long falseHave, @Nullable Instant requestsPausedUntil) {

   public record TopCategory(Long categoryId, String name, int count) {
   }
}
