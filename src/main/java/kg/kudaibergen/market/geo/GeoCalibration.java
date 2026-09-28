package kg.kudaibergen.market.geo;

import java.util.List;

/**
 * Привязка GPS к схеме по опорным точкам (углы рынка). Широта/долгота сначала переводятся
 * в локальные метры вокруг центра опорных точек, затем МНК подбирает аффинное преобразование
 * x = a·E + b·N + c, y = d·E + e·N + f. Масштаб схемы (м/px) берётся из определителя матрицы.
 */
public record GeoCalibration(double lat0, double lon0, double a, double b, double c, double d, double e, double f) {

   private static final double METERS_PER_DEGREE_LAT = 110_574;
   private static final double METERS_PER_DEGREE_LON_AT_EQUATOR = 111_320;

   public record Anchor(double lat, double lon, double x, double y) {
   }

   /** Нужно минимум 3 точки, не лежащие на одной прямой. */
   public static GeoCalibration fit(List<Anchor> anchors) {
      if (anchors.size() < 3) {
         throw new IllegalArgumentException("Нужно минимум 3 опорные точки");
      }
      double lat0 = anchors.stream().mapToDouble(Anchor::lat).average().orElseThrow();
      double lon0 = anchors.stream().mapToDouble(Anchor::lon).average().orElseThrow();

      // нормальные уравнения для [E N 1]·[a b c]ᵀ = x (и то же для y)
      double[][] m = new double[3][3];
      double[] rx = new double[3];
      double[] ry = new double[3];
      for (Anchor anchor : anchors) {
         double[] row = {east(anchor.lon(), lon0, lat0), north(anchor.lat(), lat0), 1};
         for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
               m[i][j] += row[i] * row[j];
            }
            rx[i] += row[i] * anchor.x();
            ry[i] += row[i] * anchor.y();
         }
      }
      double[] xs = solve(m, rx);
      double[] ys = solve(m, ry);
      return new GeoCalibration(lat0, lon0, xs[0], xs[1], xs[2], ys[0], ys[1], ys[2]);
   }

   public Point toMap(double lat, double lon) {
      double east = east(lon, lon0, lat0);
      double north = north(lat, lat0);
      return new Point(a * east + b * north + c, d * east + e * north + f);
   }

   /** Сколько метров в одном пикселе схемы. */
   public double metersPerPx() {
      return 1 / Math.sqrt(Math.abs(a * e - b * d));
   }

   /** Невязка опорной точки в метрах — насколько точно легла калибровка. */
   public double residualMeters(Anchor anchor) {
      return toMap(anchor.lat(), anchor.lon()).distance(new Point(anchor.x(), anchor.y())) * metersPerPx();
   }

   private static double east(double lon, double lon0, double lat0) {
      return (lon - lon0) * METERS_PER_DEGREE_LON_AT_EQUATOR * Math.cos(Math.toRadians(lat0));
   }

   private static double north(double lat, double lat0) {
      return (lat - lat0) * METERS_PER_DEGREE_LAT;
   }

   /** Гаусс с выбором главного элемента для 3×3. */
   private static double[] solve(double[][] source, double[] rhs) {
      int n = rhs.length;
      double[][] m = new double[n][n + 1];
      double scale = 0;
      for (int i = 0; i < n; i++) {
         System.arraycopy(source[i], 0, m[i], 0, n);
         m[i][n] = rhs[i];
         for (int j = 0; j < n; j++) {
            scale = Math.max(scale, Math.abs(source[i][j]));
         }
      }
      double singular = scale * 1e-9;
      for (int col = 0; col < n; col++) {
         int pivot = col;
         for (int row = col + 1; row < n; row++) {
            if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) {
               pivot = row;
            }
         }
         if (Math.abs(m[pivot][col]) <= singular) {
            throw new IllegalArgumentException("Опорные точки лежат на одной прямой — нужна ещё точка в стороне");
         }
         double[] swap = m[col];
         m[col] = m[pivot];
         m[pivot] = swap;
         for (int row = 0; row < n; row++) {
            if (row != col) {
               double factor = m[row][col] / m[col][col];
               for (int k = col; k <= n; k++) {
                  m[row][k] -= factor * m[col][k];
               }
            }
         }
      }
      double[] result = new double[n];
      for (int i = 0; i < n; i++) {
         result[i] = m[i][n] / m[i][i];
      }
      return result;
   }
}
