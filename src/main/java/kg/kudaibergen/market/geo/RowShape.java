package kg.kudaibergen.market.geo;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;

/**
 * Форма ряда: контур для отрисовки и осевая линия, вдоль которой стоят контейнеры.
 * Прямоугольник делится вдоль длинной стороны; у многоугольника (ряды А, З) ось задана явно.
 * Контейнер с позицией pos из N на стороне — ячейка N-й доли оси, сдвинутая к своей стороне.
 */
public final class RowShape {

   private final List<Point> outline;
   private final List<Point> axis;
   private final double halfWidth;
   private final double[] cumulative;

   private RowShape(List<Point> outline, List<Point> axis, double halfWidth) {
      if (axis.size() < 2) {
         throw new IllegalArgumentException("Осевая линия ряда должна содержать хотя бы 2 точки");
      }
      this.outline = List.copyOf(outline);
      this.axis = List.copyOf(axis);
      this.halfWidth = halfWidth;
      this.cumulative = new double[axis.size()];
      for (int i = 1; i < axis.size(); i++) {
         cumulative[i] = cumulative[i - 1] + axis.get(i).distance(axis.get(i - 1));
      }
   }

   /** {"rect":{x,y,w,h}} или {"polygon":[[x,y]…],"axis":[[x,y]…],"halfWidth":n}. */
   public static RowShape parse(JsonNode geometry, RowType type) {
      JsonNode rect = geometry.get("rect");
      if (rect != null) {
         double x = rect.get("x").asDouble();
         double y = rect.get("y").asDouble();
         double w = rect.get("w").asDouble();
         double h = rect.get("h").asDouble();
         List<Point> outline = List.of(new Point(x, y), new Point(x + w, y), new Point(x + w, y + h), new Point(x, y + h));
         boolean vertical = type == RowType.VROW;
         List<Point> axis = vertical
               ? List.of(new Point(x + w / 2, y), new Point(x + w / 2, y + h))
               : List.of(new Point(x, y + h / 2), new Point(x + w, y + h / 2));
         return new RowShape(outline, axis, vertical ? w / 2 : h / 2);
      }
      JsonNode polygon = geometry.get("polygon");
      if (polygon == null || geometry.get("axis") == null || geometry.get("halfWidth") == null) {
         throw new IllegalArgumentException("Геометрия ряда: нужен rect или polygon + axis + halfWidth");
      }
      return new RowShape(points(polygon), points(geometry.get("axis")), geometry.get("halfWidth").asDouble());
   }

   public static List<Point> points(JsonNode array) {
      List<Point> points = new ArrayList<>();
      array.forEach(point -> points.add(new Point(point.get(0).asDouble(), point.get(1).asDouble())));
      return points;
   }

   public double length() {
      return cumulative[cumulative.length - 1];
   }

   /** Центр контейнера pos (с 1) из count на стороне. */
   public Point containerCenter(Side side, int pos, int count) {
      return offset(side, pos, count, halfWidth / 2);
   }

   /** Точка на краю ряда перед контейнером — «дверь», к ней строится маршрут. */
   public Point containerDoor(Side side, int pos, int count) {
      return offset(side, pos, count, halfWidth);
   }

   /** Угол оси в градусах в месте контейнера — чтобы клиент повернул прямоугольник ячейки. */
   public double angleAt(int pos, int count) {
      Point direction = directionAt(along(pos, count));
      return Math.round(Math.toDegrees(Math.atan2(direction.y(), direction.x())) * 10) / 10.0;
   }

   /** Длина ячейки вдоль оси. */
   public double cellLength(int count) {
      return count == 0 ? 0 : length() / count;
   }

   public Point center() {
      return pointAt(length() / 2);
   }

   public List<Point> outline() {
      return outline;
   }

   public List<Point> axis() {
      return axis;
   }

   public double halfWidth() {
      return halfWidth;
   }

   private Point offset(Side side, int pos, int count, double distance) {
      double along = along(pos, count);
      return pointAt(along).plus(normal(directionAt(along), side).times(distance));
   }

   private double along(int pos, int count) {
      return (pos - 0.5) * length() / count;
   }

   private Point pointAt(double distance) {
      int i = segmentAt(distance);
      Point a = axis.get(i);
      Point b = axis.get(i + 1);
      double segment = cumulative[i + 1] - cumulative[i];
      double t = segment == 0 ? 0 : (distance - cumulative[i]) / segment;
      return a.plus(b.minus(a).times(t));
   }

   private Point directionAt(double distance) {
      int i = segmentAt(distance);
      return axis.get(i + 1).minus(axis.get(i)).normalized();
   }

   private int segmentAt(double distance) {
      for (int i = 0; i < axis.size() - 2; i++) {
         if (distance <= cumulative[i + 1]) {
            return i;
         }
      }
      return axis.size() - 2;
   }

   /** Перпендикуляр к оси, смотрящий в сторону side. */
   private static Point normal(Point direction, Side side) {
      Point perpendicular = new Point(-direction.y(), direction.x());
      return perpendicular.dot(side.direction()) >= 0 ? perpendicular : perpendicular.times(-1);
   }
}
