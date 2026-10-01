package kg.kudaibergen.market.route;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import kg.kudaibergen.market.geo.Point;

/**
 * Кратчайший путь по проходам (Dijkstra) от произвольной точки до «двери» контейнера.
 * Обе точки сначала притягиваются к ближайшему проходу; результат — полилиния
 * и участки с номерами проходов, из которых потом собираются шаги текстом.
 */
public final class Router {

   private final PassageGraph graph;

   public Router(PassageGraph graph) {
      this.graph = graph;
   }

   public Path route(Point from, Point to) {
      PassageGraph.Snap start = graph.snap(from);
      PassageGraph.Snap finish = graph.snap(to);
      List<Point> nodes = graph.nodes();

      double[] distance = new double[nodes.size()];
      int[] previous = new int[nodes.size()];
      int[] viaPassage = new int[nodes.size()];
      Arrays.fill(distance, Double.MAX_VALUE);
      Arrays.fill(previous, -1);
      PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[1], b[1]));
      for (int node : new int[] {start.edge().from(), start.edge().to()}) {
         double d = start.point().distance(nodes.get(node));
         if (d < distance[node]) {
            distance[node] = d;
            viaPassage[node] = start.edge().passage();
            queue.add(new double[] {node, d});
         }
      }
      while (!queue.isEmpty()) {
         double[] current = queue.poll();
         int node = (int) current[0];
         if (current[1] > distance[node]) {
            continue;
         }
         for (PassageGraph.Edge edge : graph.neighbours(node)) {
            double d = distance[node] + edge.length();
            if (d < distance[edge.to()]) {
               distance[edge.to()] = d;
               previous[edge.to()] = node;
               viaPassage[edge.to()] = edge.passage();
               queue.add(new double[] {edge.to(), d});
            }
         }
      }

      // выход с графа на точку финиша: через один из концов её ребра или напрямую по тому же ребру
      int last = -1;
      double best = Double.MAX_VALUE;
      for (int node : new int[] {finish.edge().from(), finish.edge().to()}) {
         double d = distance[node] + nodes.get(node).distance(finish.point());
         if (d < best) {
            best = d;
            last = node;
         }
      }
      boolean sameEdge = sameEdge(start.edge(), finish.edge());
      List<Leg> legs = new ArrayList<>();
      if (sameEdge && start.point().distance(finish.point()) <= best) {
         legs.add(new Leg(start.edge().passage(), List.of(start.point(), finish.point())));
      } else {
         List<Integer> chain = new ArrayList<>();
         for (int node = last; node >= 0; node = previous[node]) {
            chain.add(node);
         }
         Collections.reverse(chain);
         addLeg(legs, start.edge().passage(), start.point(), nodes.get(chain.get(0)));
         for (int i = 1; i < chain.size(); i++) {
            addLeg(legs, viaPassage[chain.get(i)], nodes.get(chain.get(i - 1)), nodes.get(chain.get(i)));
         }
         addLeg(legs, finish.edge().passage(), nodes.get(last), finish.point());
      }
      return new Path(start.point(), finish.point(), legs);
   }

   private static boolean sameEdge(PassageGraph.Edge a, PassageGraph.Edge b) {
      return (a.from() == b.from() && a.to() == b.to()) || (a.from() == b.to() && a.to() == b.from());
   }

   /** Склеивает соседние отрезки одного прохода в один участок. */
   private static void addLeg(List<Leg> legs, int passage, Point a, Point b) {
      if (a.distance(b) < 1e-6) {
         return;
      }
      if (!legs.isEmpty() && legs.get(legs.size() - 1).passage() == passage) {
         Leg previous = legs.remove(legs.size() - 1);
         List<Point> points = new ArrayList<>(previous.points());
         points.add(b);
         legs.add(new Leg(passage, points));
      } else {
         legs.add(new Leg(passage, List.of(a, b)));
      }
   }

   /** Участок маршрута по одному проходу. */
   public record Leg(int passage, List<Point> points) {

      public double length() {
         double length = 0;
         for (int i = 1; i < points.size(); i++) {
            length += points.get(i).distance(points.get(i - 1));
         }
         return length;
      }

      public Point startDirection() {
         return points.get(1).minus(points.get(0)).normalized();
      }

      public Point endDirection() {
         return points.get(points.size() - 1).minus(points.get(points.size() - 2)).normalized();
      }
   }

   /** Маршрут: точки входа на проходы и выхода с них, участки по проходам. */
   public record Path(Point start, Point finish, List<Leg> legs) {

      public double length() {
         return legs.stream().mapToDouble(Leg::length).sum();
      }

      public List<Point> polyline() {
         List<Point> points = new ArrayList<>();
         for (Leg leg : legs) {
            for (Point point : leg.points()) {
               if (points.isEmpty() || points.get(points.size() - 1).distance(point) > 1e-6) {
                  points.add(point);
               }
            }
         }
         if (points.isEmpty()) {
            points.add(start);
         }
         return points;
      }
   }
}
