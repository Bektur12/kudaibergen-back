package kg.kudaibergen.shop;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.market.ContainerTenants;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.shop.entity.Shop;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Занятость контейнеров для карты и сетки 10а. Занято и место, куда магазин переезжает (ждёт проверки),
 * но покупателю показываем магазин только в его текущем контейнере и только действующий.
 */
@Component
public class ShopContainerTenants implements ContainerTenants {

   private final ShopRepository shops;
   private final MediaService media;

   public ShopContainerTenants(ShopRepository shops, MediaService media) {
      this.shops = shops;
      this.media = media;
   }

   @Override
   @Transactional(readOnly = true)
   public Map<Long, Tenant> byContainers(Collection<Long> containerIds) {
      Map<Long, Tenant> result = new HashMap<>();
      List<Shop> occupying = shops.findOccupying(containerIds);
      Map<Long, String> avatars = media.thumbUrls(occupying.stream().map(Shop::getAvatarMediaId).toList());
      for (Shop shop : occupying) {
         boolean visible = shop.isActive();
         if (containerIds.contains(shop.getContainerId())) {
            result.put(shop.getContainerId(), visible ? new Tenant(shop.getId(), shop.getName(),
                  avatars.get(shop.getAvatarMediaId()), Set.copyOf(shop.getBrandIds())) : HIDDEN);
         }
         if (shop.getPendingContainerId() != null && containerIds.contains(shop.getPendingContainerId())) {
            result.put(shop.getPendingContainerId(), HIDDEN);
         }
      }
      return result;
   }

   /** Место занято, но магазин покупателю не показываем (на проверке, заблокирован, переезжает). */
   private static final Tenant HIDDEN = new Tenant(null, null, null, Set.of());
}
