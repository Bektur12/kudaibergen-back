package kg.kudaibergen.catalog;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.notification.push.PushMessage;
import kg.kudaibergen.notification.push.QuietHours;
import kg.kudaibergen.notification.push.UserPushes;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopMember;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * «Подешевело» и «Закончилось» (ТЗ 5.5, второй этап) — тем, у кого запчасть в избранном. Людям самого
 * бокса не шлём. Один человек получает по одной запчасти не больше одного пуша каждого вида в сутки,
 * даже если продавец несколько раз правит цену.
 */
@Component
public class FavoritePartNotifier {

   private static final Duration DEDUP = Duration.ofDays(1);

   private final PartRepository parts;
   private final ShopRepository shops;
   private final ShopMemberRepository members;
   private final FavoritePartRepository favorites;
   private final UserPushes pushes;
   private final QuietHours quietHours;
   private final StringRedisTemplate redis;

   public FavoritePartNotifier(PartRepository parts, ShopRepository shops, ShopMemberRepository members,
                               FavoritePartRepository favorites, UserPushes pushes, QuietHours quietHours,
                               StringRedisTemplate redis) {
      this.parts = parts;
      this.shops = shops;
      this.members = members;
      this.favorites = favorites;
      this.pushes = pushes;
      this.quietHours = quietHours;
      this.redis = redis;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(PartEvents.PriceDropped event) {
      notify(event.partId(), "PRICE_DROP", (part, shop) -> (lang, title) -> new PushMessage(
            (lang == Lang.KG ? "Арзандады: " : "Подешевело: ") + title,
            price(event.oldPrice()) + " → " + price(event.newPrice()) + " сом · " + shop.getName(),
            data("PRICE_DROP", part), null, !quietHours.now()));
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(PartEvents.OutOfStock event) {
      notify(event.partId(), "OUT_OF_STOCK", (part, shop) -> (lang, title) -> new PushMessage(
            (lang == Lang.KG ? "Бүттү: " : "Закончилось: ") + title,
            shop.getName() + (lang == Lang.KG ? ": азыр жок — чатка жазып сураңыз" : ": нет в наличии — спросите в чате"),
            data("OUT_OF_STOCK", part), null, !quietHours.now()));
   }

   private void notify(Long partId, String kind,
                       BiFunction<Part, Shop, BiFunction<Lang, String, PushMessage>> factory) {
      Part part = parts.findById(partId).orElse(null);
      if (part == null) {
         return;
      }
      Shop shop = shops.findById(part.getShopId()).orElseThrow();
      Set<Long> staff = members.findByShopIdOrderByCreatedAtAsc(shop.getId()).stream()
            .map(ShopMember::getUserId).collect(Collectors.toSet());
      List<Long> recipients = favorites.usersWhoFavorited(partId).stream()
            .filter(userId -> !staff.contains(userId))
            .filter(userId -> Boolean.TRUE.equals(redis.opsForValue()
                  .setIfAbsent("fav:" + kind + ":" + partId + ":" + userId, "1", DEDUP)))
            .toList();
      BiFunction<Lang, String, PushMessage> message = factory.apply(part, shop);
      pushes.send(recipients, (user, settings) -> message.apply(user.getLang(), part.getTitle()));
   }

   private static Map<String, String> data(String type, Part part) {
      return Map.of("type", type, "partId", part.getId().toString(), "shopId", part.getShopId().toString());
   }

   /** «3 200» — тысячи через пробел, как в макете. */
   static String price(int value) {
      return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
   }
}
