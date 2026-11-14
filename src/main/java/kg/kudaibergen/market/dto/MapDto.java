package kg.kudaibergen.market.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import kg.kudaibergen.market.ContainerTenants;
import kg.kudaibergen.market.geo.GeoCalibration;
import org.springframework.lang.Nullable;

/**
 * Вся схема рынка одним ответом (экраны 15, 18). Координаты — пиксели схемы, ось Y вниз.
 * Клиент хранит кэш и перекачивает карту, только если сменился version (ETag "map-v{version}").
 * geo — матрица GPS → x,y (null, пока админ не задал опорные точки): точку «я здесь» можно считать без сети.
 * rows — только ряды, нарисованные на схеме: ряд без формы (geometry не задана) есть лишь в GET /rows
 * (onMap = false) и в сетке контейнеров GET /rows/{id}/containers.
 */
public record MapDto(int version, String coordinateSystem, JsonNode boundary, JsonNode blocks,
                     List<PassageDto> passages, JsonNode entrances, JsonNode pois, JsonNode streets, JsonNode labels,
                     List<MapRowDto> rows, double metersPerPx, @Nullable GeoCalibration geo) {

   public record PassageDto(List<double[]> points, double width) {
   }

   /** Ряд: исходная геометрия + осевая линия, вдоль которой стоят контейнеры. */
   public record MapRowDto(Long id, String code, String label, RowType type, JsonNode geometry, double[] center,
                           List<double[]> axis, double halfWidth, List<MapContainerDto> containers) {
   }

   /**
    * Контейнер: центр ячейки, угол оси ряда и длина ячейки — чтобы нарисовать прямоугольник с номером.
    * shop — магазин в контейнере (тап по контейнеру → профиль продавца); null — свободен или продавца нет в приложении.
    */
   public record MapContainerDto(Long id, int number, Side side, double x, double y, double angle, double length,
                                 @Nullable ContainerTenants.Tenant shop) {

      MapContainerDto withShop(@Nullable ContainerTenants.Tenant tenant) {
         return new MapContainerDto(id, number, side, x, y, angle, length, tenant);
      }
   }

   /** Отпечаток магазинов в контейнерах — часть ETag: новый продавец на карте без смены версии схемы. */
   public String shopsTag() {
      int hash = 1;
      for (MapRowDto row : rows) {
         for (MapContainerDto c : row.containers()) {
            if (c.shop() != null) {
               hash = 31 * hash + java.util.Objects.hash(c.id(), c.shop().shopId(), c.shop().name(), c.shop().avatarUrl());
            }
         }
      }
      return Integer.toHexString(hash);
   }

   /** Та же карта, но с магазинами в контейнерах (схема кэшируется, магазины — свежие на каждый запрос). */
   public MapDto withShops(java.util.Map<Long, ContainerTenants.Tenant> tenants) {
      if (tenants.isEmpty()) {
         return this;
      }
      List<MapRowDto> withShops = rows.stream().map(row -> new MapRowDto(row.id(), row.code(), row.label(), row.type(),
            row.geometry(), row.center(), row.axis(), row.halfWidth(),
            row.containers().stream().map(c -> c.withShop(tenants.get(c.id()))).toList())).toList();
      return new MapDto(version, coordinateSystem, boundary, blocks, passages, entrances, pois, streets, labels,
            withShops, metersPerPx, geo);
   }
}
