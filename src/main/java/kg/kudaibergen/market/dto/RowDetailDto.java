package kg.kudaibergen.market.dto;

import java.util.List;

import kg.kudaibergen.market.ContainerTenants;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;

/**
 * Ряд с контейнерами по сторонам: сетка выбора бокса (10а, занятые — occupied) и тап по ряду на карте (15).
 * shop = null — «Нет продавца в приложении».
 */
public record RowDetailDto(Long id, String code, String label, RowType type, List<SideDto> sides) {

   public record SideDto(Side side, List<ContainerSlotDto> containers) {
   }

   public record ContainerSlotDto(Long id, int number, boolean occupied, ContainerTenants.Tenant shop) {
   }
}
