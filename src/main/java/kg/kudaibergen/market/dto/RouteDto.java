package kg.kudaibergen.market.dto;

import java.util.List;

import kg.kudaibergen.market.route.RouteSteps;

/**
 * Маршрут до контейнера (экран 18): «1 мин · 50 м пешком», полилиния по проходам и шаги текстом.
 * fromSource — откуда построен: POINT (x,y с клиента), GPS, ENTRANCE (нет геолокации — от главного входа).
 */
public record RouteDto(LocationDto target, double[] containerPoint, double[] from, FromSource fromSource,
                       List<double[]> polyline, int distanceM, int minutes, List<RouteSteps.Step> steps) {

   public enum FromSource {
      POINT, GPS, ENTRANCE
   }
}
