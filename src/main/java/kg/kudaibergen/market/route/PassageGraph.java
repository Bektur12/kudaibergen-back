package kg.kudaibergen.market.route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import kg.kudaibergen.market.geo.Geometry;
import kg.kudaibergen.market.geo.Point;

/**
 * Граф проходов для маршрута (экран 18). Вершины — изломы и перекрёстки проходов,
 * рёбра — участки проходов между ними, вес — длина в пикселях схемы.
 *
 * <p>Схема нарисована от руки, поэтому граф строится с допусками:
 * <ul>
 *    <li>пересечения и Т-образные примыкания ищутся с допуском {@value #CROSS_TOLERANCE} px;</li>
 *    <li>конец прохода продлевается по направлению до {@value #EXTEND_TOLERANCE} px до первого встречного прохода;</li>
 *    <li>висящий конец, не дотянутый до соседнего прохода меньше чем на {@value #SNAP_TOLERANCE} px, пристыковывается к нему;</li>
 *    <li>оторванные куски сети соединяются «мостиками» между ближайшими вершинами
 *        (сначала все пары ближе {@value #BRIDGE_TOLERANCE} px, затем — ближайшая пара к основной сети).</li>
 * </ul>
 */
public final class PassageGraph {

   static final double CROSS_TOLERANCE = 1.5;
   static final double EXTEND_TOLERANCE = 50;
   static final double SNAP_TOLERANCE = 80;
   static final double BRIDGE_TOLERANCE = 80;
   private static final double MERGE_DISTANCE = 1.0;
   /** Проход «мостика» — у него нет своего названия. */
   public static final int BRIDGE = -1;

   private final List<Point> nodes = new ArrayList<>();
   private final List<List<Edge>> adjacency = new ArrayList<>();
   private final List<Edge> edges = new ArrayList<>();

   private PassageGraph() {
   }

   /** passages — полилинии проходов; индекс в списке = номер прохода в рёбрах. */
   public static PassageGraph build(List<List<Point>> passages) {
      PassageGraph graph = new PassageGraph();
      List<Segment> segments = new ArrayList<>();
      for (int p = 0; p < passages.size(); p++) {
         List<Point> line = passages.get(p);
         for (int i = 0; i + 1 < line.size(); i++) {
            segments.add(new Segment(p, i, line.get(i), line.get(i + 1)));
         }
      }

      // пересечения и примыкания проходов друг к другу
      for (int i = 0; i < segments.size(); i++) {
         for (int j = i + 1; j < segments.size(); j++) {
            Segment first = segments.get(i);
            Segment second = segments.get(j);
            if (first.passage == second.passage && Math.abs(first.index - second.index) <= 1) {
               continue;
            }
            double[] hit = Geometry.intersect(first.a, first.b, second.a, second.b, CROSS_TOLERANCE);
            if (hit != null) {
               first.splits.add(hit[0]);
               second.splits.add(hit[1]);
            }
         }
      }

      // концы проходов: продолжение до следующего прохода или стыковка висящего конца
      List<Point[]> connectors = new ArrayList<>();
      List<Integer> connectorPassages = new ArrayList<>();
      for (int p = 0; p < passages.size(); p++) {
         List<Point> line = passages.get(p);
         Point[][] ends = {
               {line.get(0), line.get(0).minus(line.get(1)).normalized()},
               {line.get(line.size() - 1), line.get(line.size() - 1).minus(line.get(line.size() - 2)).normalized()}};
         for (Point[] end : ends) {
            Point point = end[0];
            if (!extend(segments, p, point, end[1], connectors, connectorPassages)) {
               snapDangling(segments, p, point, connectors, connectorPassages);
            }
         }
      }

      for (Segment segment : segments) {
         Point previous = null;
         for (double t : segment.splits) {
            Point point = segment.a.plus(segment.b.minus(segment.a).times(t));
            if (previous != null) {
               graph.connect(previous, point, segment.passage);
            }
            previous = point;
         }
      }
      for (int i = 0; i < connectors.size(); i++) {
         graph.connect(connectors.get(i)[0], connectors.get(i)[1], connectorPassages.get(i));
      }
      graph.bridgeComponents();
      return graph;
   }

   /**
    * Проход, который «упирается» в соседний чуть-чуть не доходя (горизонтальные проходы между рядами
    * заканчиваются у торцов рядов, а центральный проход идёт на 40 px дальше): продлеваем конец
    * по направлению прохода до {@value #EXTEND_TOLERANCE} px и соединяем с первым встречным проходом.
    */
   private static boolean extend(List<Segment> segments, int passage, Point end, Point direction,
                                 List<Point[]> connectors, List<Integer> connectorPassages) {
      Point far = end.plus(direction.times(EXTEND_TOLERANCE));
      Segment hitSegment = null;
      double hitT = 0;
      double hitDistance = Double.MAX_VALUE;
      for (Segment segment : segments) {
         if (segment.passage == passage) {
            continue;
         }
         double[] hit = Geometry.intersect(end, far, segment.a, segment.b, 0);
         if (hit == null) {
            continue;
         }
         double distance = hit[0] * EXTEND_TOLERANCE;
         if (distance > CROSS_TOLERANCE && distance < hitDistance) {
            hitDistance = distance;
            hitSegment = segment;
            hitT = hit[1];
         }
      }
      if (hitSegment == null) {
         return false;
      }
      hitSegment.splits.add(hitT);
      connectors.add(new Point[] {end, hitSegment.a.plus(hitSegment.b.minus(hitSegment.a).times(hitT))});
      connectorPassages.add(passage);
      return true;
   }

   /** Висящий конец (ни с чем не соприкасается) — к ближайшей точке другого прохода в пределах допуска. */
   private static void snapDangling(List<Segment> segments, int passage, Point end,
                                    List<Point[]> connectors, List<Integer> connectorPassages) {
      Segment nearest = null;
      Geometry.Projection best = null;
      for (Segment segment : segments) {
         if (segment.passage == passage) {
            continue;
         }
         Geometry.Projection projection = Geometry.project(segment.a, segment.b, end);
         if (best == null || projection.distance() < best.distance()) {
            best = projection;
            nearest = segment;
         }
      }
      if (nearest != null && best.distance() > CROSS_TOLERANCE && best.distance() <= SNAP_TOLERANCE) {
         nearest.splits.add(best.t());
         connectors.add(new Point[] {end, best.point()});
         connectorPassages.add(passage);
      }
   }

   public List<Point> nodes() {
      return nodes;
   }

   public List<Edge> edges() {
      return edges;
   }

   List<Edge> neighbours(int node) {
      return adjacency.get(node);
   }

   /** Ближайшая к точке позиция на проходах. */
   public Snap snap(Point point) {
      Snap best = null;
      for (Edge edge : edges) {
         Geometry.Projection projection = Geometry.project(nodes.get(edge.from()), nodes.get(edge.to()), point);
         if (best == null || projection.distance() < best.distance()) {
            best = new Snap(edge, projection.point(), projection.distance());
         }
      }
      return best;
   }

   /** Число связных кусков сети (после мостиков должно быть 1). */
   public int componentCount() {
      int[] component = components();
      return (int) java.util.Arrays.stream(component).distinct().count();
   }

   private void connect(Point a, Point b, int passage) {
      int from = nodeAt(a);
      int to = nodeAt(b);
      if (from == to) {
         return;
      }
      for (Edge existing : adjacency.get(from)) {
         if (existing.to() == to) {
            return;
         }
      }
      double length = nodes.get(from).distance(nodes.get(to));
      Edge forward = new Edge(from, to, length, passage);
      edges.add(forward);
      adjacency.get(from).add(forward);
      adjacency.get(to).add(new Edge(to, from, length, passage));
   }

   private int nodeAt(Point point) {
      for (int i = 0; i < nodes.size(); i++) {
         if (nodes.get(i).distance(point) < MERGE_DISTANCE) {
            return i;
         }
      }
      nodes.add(point);
      adjacency.add(new ArrayList<>());
      return nodes.size() - 1;
   }

   private void bridgeComponents() {
      int[] component = components();
      for (int i = 0; i < nodes.size(); i++) {
         for (int j = i + 1; j < nodes.size(); j++) {
            if (component[i] != component[j] && nodes.get(i).distance(nodes.get(j)) <= BRIDGE_TOLERANCE) {
               connect(nodes.get(i), nodes.get(j), BRIDGE);
            }
         }
      }
      component = components();
      while (java.util.Arrays.stream(component).distinct().count() > 1) {
         int main = largest(component);
         int bestI = -1;
         int bestJ = -1;
         double bestDistance = Double.MAX_VALUE;
         for (int i = 0; i < nodes.size(); i++) {
            if (component[i] != main) {
               continue;
            }
            for (int j = 0; j < nodes.size(); j++) {
               double distance = nodes.get(i).distance(nodes.get(j));
               if (component[j] != main && distance < bestDistance) {
                  bestDistance = distance;
                  bestI = i;
                  bestJ = j;
               }
            }
         }
         connect(nodes.get(bestI), nodes.get(bestJ), BRIDGE);
         component = components();
      }
   }

   private int[] components() {
      int[] component = new int[nodes.size()];
      java.util.Arrays.fill(component, -1);
      int next = 0;
      for (int start = 0; start < nodes.size(); start++) {
         if (component[start] >= 0) {
            continue;
         }
         java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
         queue.add(start);
         component[start] = next;
         while (!queue.isEmpty()) {
            for (Edge edge : adjacency.get(queue.poll())) {
               if (component[edge.to()] < 0) {
                  component[edge.to()] = next;
                  queue.add(edge.to());
               }
            }
         }
         next++;
      }
      return component;
   }

   private static int largest(int[] component) {
      java.util.Map<Integer, Long> sizes = java.util.Arrays.stream(component).boxed()
            .collect(java.util.stream.Collectors.groupingBy(c -> c, java.util.stream.Collectors.counting()));
      return sizes.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).orElseThrow().getKey();
   }

   /** Ребро графа: участок прохода passage (или BRIDGE) длиной length px. */
   public record Edge(int from, int to, double length, int passage) {
   }

   /** Позиция на проходе: ребро, точка на нём и расстояние до исходной точки. */
   public record Snap(Edge edge, Point point, double distance) {
   }

   private static final class Segment {
      final int passage;
      final int index;
      final Point a;
      final Point b;
      final TreeSet<Double> splits = new TreeSet<>(Comparator.naturalOrder());

      Segment(int passage, int index, Point a, Point b) {
         this.passage = passage;
         this.index = index;
         this.a = a;
         this.b = b;
         splits.add(0.0);
         splits.add(1.0);
      }
   }
}
