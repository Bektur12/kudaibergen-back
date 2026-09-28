package kg.kudaibergen.shop;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import kg.kudaibergen.market.ContainerTenants;
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

   public ShopContainerTenants(ShopRepository shops) {
      this.shops = shops;
   }

   @Override
   @Transactional(readOnly = true)
   public Map<Long, Tenant> byContainers(Collection<Long> containerIds) {
      Map<Long, Tenant> result = new HashMap<>();
      for (Shop shop : shops.findOccupying(containerIds)) {
         boolean visible = shop.isActive();
         if (containerIds.contains(shop.getContainerId())) {
            result.put(shop.getContainerId(), visible ? new Tenant(shop.getId(), shop.getName(), null) : HIDDEN);
         }
         if (shop.getPendingContainerId() != null && containerIds.contains(shop.getPendingContainerId())) {
            result.put(shop.getPendingContainerId(), HIDDEN);
         }
      }
      return result;
   }

   /** Место занято, но магазин покупателю не показываем (на проверке, заблокирован, переезжает). */
   private static final Tenant HIDDEN = new Tenant(null, null, null);
}
