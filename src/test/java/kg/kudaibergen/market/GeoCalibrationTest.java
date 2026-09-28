package kg.kudaibergen.market;

import java.util.List;

import kg.kudaibergen.market.geo.GeoCalibration;
import kg.kudaibergen.market.geo.Point;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GeoCalibrationTest {

   /** Условные углы рынка в Бишкеке: схема повёрнута и масштабирована 0,25 м/px. */
   private static final List<GeoCalibration.Anchor> CORNERS = List.of(
         anchor(42.8890, 74.6200, 80, 38),
         anchor(42.8890, 74.6285, 835, 35),
         anchor(42.8780, 74.6320, 1330, 843),
         anchor(42.8770, 74.6215, 238, 1375));

   @Test
   void точкиКалибровкиЛожатсяНаСвоиМеста() {
      GeoCalibration calibration = GeoCalibration.fit(CORNERS);
      for (GeoCalibration.Anchor anchor : CORNERS) {
         // четыре точки, шесть параметров: невязки есть, но это сотня метров, не километры
         assertThat(calibration.residualMeters(anchor)).isLessThan(150);
      }
      assertThat(calibration.metersPerPx()).isBetween(0.5, 1.5);
   }

   @Test
   void точнаяАффиннаяМатрицаВосстанавливается() {
      // x = 2·E + 0.5·N + 400, y = -0.3·E - 2·N + 700 (в метрах от центра)
      List<GeoCalibration.Anchor> exact = List.of(
            exactAnchor(0, 0), exactAnchor(100, 0), exactAnchor(0, 100), exactAnchor(-80, 60));
      GeoCalibration calibration = GeoCalibration.fit(exact);
      for (GeoCalibration.Anchor anchor : exact) {
         assertThat(calibration.residualMeters(anchor)).isLessThan(0.01);
      }
      Point middle = calibration.toMap(42.88, 74.62);
      assertThat(middle.x()).isCloseTo(expectedX(0, 0), within(1.0));
   }

   @Test
   void точкиНаОднойПрямойОтклоняются() {
      assertThatThrownBy(() -> GeoCalibration.fit(List.of(
            anchor(42.88, 74.62, 0, 0), anchor(42.881, 74.621, 10, 10), anchor(42.882, 74.622, 20, 20))))
            .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> GeoCalibration.fit(CORNERS.subList(0, 2)))
            .isInstanceOf(IllegalArgumentException.class);
   }

   private static GeoCalibration.Anchor anchor(double lat, double lon, double x, double y) {
      return new GeoCalibration.Anchor(lat, lon, x, y);
   }

   /** Точка на east/north метров от (42.88, 74.62) с «идеальными» координатами схемы. */
   private static GeoCalibration.Anchor exactAnchor(double east, double north) {
      double lat = 42.88 + north / 110_574;
      double lon = 74.62 + east / (111_320 * Math.cos(Math.toRadians(42.88)));
      return anchor(lat, lon, expectedX(east, north), -0.3 * east - 2 * north + 700);
   }

   private static double expectedX(double east, double north) {
      return 2 * east + 0.5 * north + 400;
   }
}
