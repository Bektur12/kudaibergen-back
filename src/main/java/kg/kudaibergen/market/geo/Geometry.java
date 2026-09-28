package kg.kudaibergen.market.geo;

import java.util.List;

/** Простая плоская геометрия для карты. */
public final class Geometry {

   private Geometry() {
   }

   /** Проекция точки на отрезок: параметр t ∈ [0,1] и сама точка. */
   public static Projection project(Point a, Point b, Point p) {
      Point ab = b.minus(a);
      double lengthSquared = ab.dot(ab);
      double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, p.minus(a).dot(ab) / lengthSquared));
      Point onSegment = a.plus(ab.times(t));
      return new Projection(t, onSegment, onSegment.distance(p));
   }

   /** Точка внутри многоугольника (чётность пересечений луча). */
   public static boolean contains(List<Point> polygon, Point p) {
      boolean inside = false;
      for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
         Point a = polygon.get(i);
         Point b = polygon.get(j);
         boolean crosses = (a.y() > p.y()) != (b.y() > p.y())
               && p.x() < (b.x() - a.x()) * (p.y() - a.y()) / (b.y() - a.y()) + a.x();
         if (crosses) {
            inside = !inside;
         }
      }
      return inside;
   }

   /**
    * Пересечение отрезков ab и cd с допуском tolerance (в пикселях по длине каждого).
    * Возвращает параметры {t на ab, u на cd} или null. Параллельные отрезки не пересекаются.
    */
   public static double[] intersect(Point a, Point b, Point c, Point d, double tolerance) {
      Point r = b.minus(a);
      Point s = d.minus(c);
      double denominator = r.cross(s);
      if (Math.abs(denominator) < 1e-9) {
         return null;
      }
      Point ac = c.minus(a);
      double t = ac.cross(s) / denominator;
      double u = ac.cross(r) / denominator;
      double tTolerance = tolerance / r.length();
      double uTolerance = tolerance / s.length();
      if (t < -tTolerance || t > 1 + tTolerance || u < -uTolerance || u > 1 + uTolerance) {
         return null;
      }
      return new double[] {clamp(t), clamp(u)};
   }

   private static double clamp(double value) {
      return Math.max(0, Math.min(1, value));
   }

   public record Projection(double t, Point point, double distance) {
   }
}
