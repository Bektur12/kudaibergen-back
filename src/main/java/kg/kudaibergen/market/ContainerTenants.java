package kg.kudaibergen.market;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.lang.Nullable;

/**
 * Кто стоит в контейнерах. Реализует модуль shops; пока его нет — все контейнеры свободны.
 * Карте и сетке экрана 10а нужны занятость и краткая карточка магазина.
 */
public interface ContainerTenants {

   Map<Long, Tenant> byContainers(Collection<Long> containerIds);

   /**
    * Магазин в контейнере: для серой клетки на 10а и тапа по контейнеру на карте (→ экран 30).
    * brandIds — марки магазина, наружу не отдаются: из них считается «продаёт Toyota» (31).
    */
   record Tenant(@Nullable Long shopId, @Nullable String name, @Nullable String avatarUrl, @JsonIgnore Set<Long> brandIds) {
   }
}
