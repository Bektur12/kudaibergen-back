package kg.kudaibergen.market;

import java.util.Collection;
import java.util.Map;

/**
 * Кто стоит в контейнерах. Реализует модуль shops; пока его нет — все контейнеры свободны.
 * Карте и сетке экрана 10а нужны занятость и краткая карточка магазина.
 */
public interface ContainerTenants {

   Map<Long, Tenant> byContainers(Collection<Long> containerIds);

   /** Магазин в контейнере: для серой клетки на 10а и тапа по контейнеру на карте (→ экран 30). */
   record Tenant(Long shopId, String name, String avatarUrl) {
   }
}
