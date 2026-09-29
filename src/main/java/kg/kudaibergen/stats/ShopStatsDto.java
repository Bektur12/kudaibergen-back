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
 *   <li>partViews — просмотры карточек запчастей бокса.</li>
 * </ul>
 */
public record ShopStatsDto(StatsPeriod period, Instant from, Instant to, int requestsByBrands, int answeredHave,
                           int answeredNotHave, int wroteInChat, int buyersArrived, int sales, int unanswered,
                           @Nullable Integer avgReplyMinutes, long partViews, List<TopCategory> topCategories) {

   public record TopCategory(Long categoryId, String name, int count) {
   }
}
