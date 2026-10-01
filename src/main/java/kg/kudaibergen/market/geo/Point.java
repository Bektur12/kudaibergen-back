package kg.kudaibergen.market.geo;

/** Точка или вектор в координатах схемы рынка (пиксели, ось Y вниз). */
public record Point(double x, double y) {

   public Point plus(Point other) {
      return new Point(x + other.x, y + other.y);
   }

   public Point minus(Point other) {
      return new Point(x - other.x, y - other.y);
   }

   public Point times(double k) {
      return new Point(x * k, y * k);
   }

   public double dot(Point other) {
      return x * other.x + y * other.y;
   }

   /**
    * Векторное произведение. Ось Y смотрит вниз, поэтому положительное значение значит
    * «other правее this» (поворот по часовой стрелке на экране).
    */
   public double cross(Point other) {
      return x * other.y - y * other.x;
   }

   public double length() {
      return Math.hypot(x, y);
   }

   public double distance(Point other) {
      return Math.hypot(x - other.x, y - other.y);
   }

   public Point normalized() {
      double length = length();
      return length == 0 ? this : new Point(x / length, y / length);
   }

   public double[] toArray() {
      return new double[] {round(x), round(y)};
   }

   private static double round(double value) {
      return Math.round(value * 10) / 10.0;
   }
}
