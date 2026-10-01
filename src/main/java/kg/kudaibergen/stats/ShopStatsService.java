package kg.kudaibergen.stats;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.catalog.PartRepository;
import kg.kudaibergen.category.Category;
import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Статистика бокса за неделю или месяц (ТЗ 12, экран 17). Видят владелец и сотрудники. */
@Service
public class ShopStatsService {

   static final int TOP_CATEGORIES = 4;

   private final ShopStatsRepository stats;
   private final PartRepository parts;
   private final ShopAccess access;
   private final CategoryService categories;
   private final Clock clock;

   public ShopStatsService(ShopStatsRepository stats, PartRepository parts, ShopAccess access,
                           CategoryService categories, Clock clock) {
      this.stats = stats;
      this.parts = parts;
      this.access = access;
      this.categories = categories;
      this.clock = clock;
   }

   @Transactional(readOnly = true)
   public ShopStatsDto forMember(Long userId, StatsPeriod period, Lang lang) {
      Long shopId = access.requireMember(userId).shop().getId();
      Instant to = clock.instant();
      Instant from = to.minus(period.length());

      ShopStatsRepository.RequestCounts requests = stats.requests(shopId, from);
      Map<Long, Category> byId = categories.byId();
      List<ShopStatsDto.TopCategory> top = stats.topCategories(shopId, from, TOP_CATEGORIES).stream()
            .filter(row -> byId.containsKey(row.categoryId()))
            .map(row -> new ShopStatsDto.TopCategory(row.categoryId(), byId.get(row.categoryId()).name(lang),
                  row.count()))
            .toList();
      return new ShopStatsDto(period, from, to, requests.received(), requests.have(), requests.notHave(),
            stats.wroteInChat(shopId, from), stats.buyersArrived(shopId, from), stats.sales(shopId, from),
            requests.missed(), avgMinutes(requests.avgReplySeconds()), parts.viewsSince(shopId, period.days()),
            top);
   }

   /** «Отвечаете в среднем за 4 мин»: не меньше минуты, если ответы были. */
   static Integer avgMinutes(Double seconds) {
      return seconds == null ? null : (int) Math.max(1, Math.round(seconds / 60));
   }
}
