package kg.kudaibergen.market.dto;

import java.util.List;

import kg.kudaibergen.market.ContainerTenants;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import org.springframework.lang.Nullable;

/**
 * Ряд с контейнерами по сторонам: сетка выбора бокса (10а, занятые — occupied), тап по ряду на карте (15)
 * и выбор контейнеров для запроса (31). shop = null — «Нет продавца в приложении».
 * brandSellers — «продают Toyota: 9», только если передан brandId.
 */
public record RowDetailDto(Long id, String code, String label, RowType type, List<SideDto> sides,
                           @Nullable Integer brandSellers) {

   public record SideDto(Side side, List<ContainerSlotDto> containers) {
   }

   /**
    * state — есть ли в контейнере продавец в приложении; sellsBrand — продаёт ли он марку brandId
    * (null без brandId). Контейнер без марки выбрать можно — клиент показывает предупреждение.
    */
   public record ContainerSlotDto(Long id, int number, boolean occupied, ContainerState state, @Nullable Boolean sellsBrand,
                                  @Nullable ContainerTenants.Tenant shop) {
   }

   public enum ContainerState {
      HAS_SELLER,
      NO_SELLER
   }
}
