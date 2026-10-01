package kg.kudaibergen.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.market.entity.Container;
import kg.kudaibergen.market.entity.MapVersion;
import kg.kudaibergen.market.entity.MarketRow;
import kg.kudaibergen.market.entity.Side;
import kg.kudaibergen.market.geo.GeoCalibration;
import kg.kudaibergen.market.geo.Geometry;
import kg.kudaibergen.market.geo.Point;
import kg.kudaibergen.market.geo.RowShape;
import kg.kudaibergen.market.route.PassageGraph;
import kg.kudaibergen.market.route.PassageName;
import kg.kudaibergen.market.route.Router;

/**
 * Разобранная текущая схема рынка: ряды с формами, контейнеры с координатами, граф проходов.
 * Неизменяемый снимок — строится целиком и подменяется атомарно.
 */
public final class MarketSnapshot {

   private final MapVersion version;
   private final JsonNode json;
   private final List<Point> boundary;
   private final List<Entrance> entrances;
   private final List<PassageName> passageNames;
   private final Router router;
   private final PassageGraph graph;
   private final double metersPerPx;
   private final GeoCalibration calibration;
   private final List<RowView> rows;
   private final Map<Long, RowView> rowsById = new HashMap<>();
   private final Map<String, RowView> rowsByToken = new HashMap<>();
   private final Map<Long, ContainerView> containersById = new HashMap<>();
   private final Map<String, ContainerView> containersByToken = new HashMap<>();

   MarketSnapshot(MapVersion version, List<MarketRow> activeRows, List<Container> activeContainers,
                  ObjectMapper mapper) {
      this.version = version;
      try {
         var node = mapper.createObjectNode();
         node.set("boundary", mapper.readTree(version.getBoundary()));
         node.set("blocks", mapper.readTree(version.getBlocks()));
         node.set("passages", mapper.readTree(version.getPassages()));
         node.set("entrances", mapper.readTree(version.getEntrances()));
         node.set("pois", mapper.readTree(version.getPois()));
         node.set("streets", mapper.readTree(version.getStreets()));
         node.set("labels", mapper.readTree(version.getLabels()));
         this.json = node;
         this.calibration = version.getGeoAffine() == null ? null
               : mapper.readValue(version.getGeoAffine(), GeoCalibration.class);

         this.boundary = RowShape.points(json.get("boundary"));
         this.entrances = new ArrayList<>();
         json.get("entrances").forEach(entrance -> entrances.add(new Entrance(
               new Point(entrance.get("x").asDouble(), entrance.get("y").asDouble()), entrance.get("label").asText())));

         List<List<Point>> lines = new ArrayList<>();
         this.passageNames = new ArrayList<>();
         for (JsonNode passage : json.get("passages")) {
            lines.add(RowShape.points(passage.get("points")));
            passageNames.add(passage.hasNonNull("name")
                  ? mapper.treeToValue(passage.get("name"), PassageName.class) : PassageName.UNKNOWN);
         }
         this.graph = PassageGraph.build(lines);
         this.router = new Router(graph);

         Map<Long, List<Container>> containersByRow = new HashMap<>();
         activeContainers.forEach(c -> containersByRow.computeIfAbsent(c.getRowId(), id -> new ArrayList<>()).add(c));
         this.rows = new ArrayList<>();
         for (MarketRow row : activeRows) {
            RowShape shape = RowShape.parse(mapper.readTree(row.getGeometry()), row.getType());
            RowView view = new RowView(row, shape, new EnumMap<>(Side.class));
            for (Side side : Side.of(row.getType())) {
               List<Container> onSide = containersByRow.getOrDefault(row.getId(), List.of()).stream()
                     .filter(c -> c.getSide() == side)
                     .sorted(Comparator.comparingInt(Container::getPosInRow))
                     .toList();
               List<ContainerView> views = new ArrayList<>();
               for (int i = 0; i < onSide.size(); i++) {
                  Container container = onSide.get(i);
                  ContainerView containerView = new ContainerView(container, view,
                        shape.containerCenter(side, i + 1, onSide.size()),
                        shape.containerDoor(side, i + 1, onSide.size()),
                        shape.angleAt(i + 1, onSide.size()), shape.cellLength(onSide.size()));
                  views.add(containerView);
                  containersById.put(container.getId(), containerView);
                  containersByToken.put(container.getQrToken(), containerView);
               }
               view.sides().put(side, views);
            }
            rows.add(view);
            rowsById.put(row.getId(), view);
            rowsByToken.put(row.getQrToken(), view);
         }
      } catch (JsonProcessingException e) {
         throw new IllegalStateException("Схема рынка v" + version.getVersion() + " повреждена", e);
      }
      this.metersPerPx = calibration != null ? calibration.metersPerPx() : version.getMetersPerPx().doubleValue();
   }

   public int version() {
      return version.getVersion();
   }

   public JsonNode json() {
      return json;
   }

   public List<RowView> rows() {
      return rows;
   }

   public Optional<RowView> row(Long id) {
      return Optional.ofNullable(rowsById.get(id));
   }

   public Optional<RowView> rowByToken(String token) {
      return Optional.ofNullable(rowsByToken.get(token));
   }

   public Optional<ContainerView> container(Long id) {
      return Optional.ofNullable(containersById.get(id));
   }

   public Optional<ContainerView> containerByToken(String token) {
      return Optional.ofNullable(containersByToken.get(token));
   }

   public List<Entrance> entrances() {
      return entrances;
   }

   public List<PassageName> passageNames() {
      return passageNames;
   }

   public Router router() {
      return router;
   }

   public PassageGraph graph() {
      return graph;
   }

   public double metersPerPx() {
      return metersPerPx;
   }

   public Optional<GeoCalibration> calibration() {
      return Optional.ofNullable(calibration);
   }

   public boolean inside(Point point) {
      return Geometry.contains(boundary, point);
   }

   /** Ряд со своей формой и активными контейнерами по сторонам (по порядку вдоль оси). */
   public record RowView(MarketRow row, RowShape shape, Map<Side, List<ContainerView>> sides) {

      public List<ContainerView> all() {
         return sides.values().stream().flatMap(List::stream).toList();
      }
   }

   /** Контейнер с вычисленными координатами: центр ячейки, «дверь» у прохода, угол оси. */
   public record ContainerView(Container container, RowView row, Point center, Point door, double angle,
                               double cellLength) {
   }

   public record Entrance(Point point, String label) {
   }
}
