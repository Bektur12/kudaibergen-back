package kg.kudaibergen.market.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import kg.kudaibergen.market.geo.GeoCalibration;
import org.springframework.lang.Nullable;

/**
 * Вся схема рынка одним ответом (экраны 15, 18). Координаты — пиксели схемы, ось Y вниз.
 * Клиент хранит кэш и перекачивает карту, только если сменился version (ETag "map-v{version}").
 * geo — матрица GPS → x,y (null, пока админ не задал опорные точки): точку «я здесь» можно считать без сети.
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

   /** Контейнер: центр ячейки, угол оси ряда и длина ячейки — чтобы нарисовать прямоугольник с номером. */
   public record MapContainerDto(Long id, int number, Side side, double x, double y, double angle, double length) {
   }
}
