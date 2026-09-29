package kg.kudaibergen.market;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.market.entity.RowType;
import kg.kudaibergen.market.entity.Side;
import kg.kudaibergen.market.geo.Point;
import kg.kudaibergen.market.geo.RowShape;
import kg.kudaibergen.market.route.PassageGraph;
import kg.kudaibergen.market.route.PassageName;
import kg.kudaibergen.market.route.RouteSteps;
import kg.kudaibergen.market.route.Router;
import kg.kudaibergen.user.entity.Lang;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Граф и маршрут на настоящей схеме из макета (design/market-map.json). */
class RouteOnDesignMapTest {

   private static final double METERS_PER_PX = 0.25;

   private static PassageGraph graph;
   private static List<PassageName> names;

   @BeforeAll
   static void load() throws Exception {
      try (InputStream in = RouteOnDesignMapTest.class.getResourceAsStream("/market-map.json")) {
         JsonNode map = new ObjectMapper().readTree(in);
         List<List<Point>> lines = new ArrayList<>();
         map.get("passages").forEach(p -> lines.add(RowShape.points(p.get("points"))));
         graph = PassageGraph.build(lines);
         names = new ArrayList<>();
         for (int i = 0; i < lines.size(); i++) {
            names.add(PassageName.UNKNOWN);
         }
         // как в сиде V3: 0 — центральный, 12 — между рядами 16 и 14
         names.set(0, new PassageName(PassageName.PassageKind.CENTRAL, null));
         names.set(12, new PassageName(PassageName.PassageKind.BETWEEN, List.of("16", "14")));
      }
   }

   @Test
   void сетьПроходовСвязнаяПослеСтыковкиИМостиков() {
      assertThat(graph.componentCount()).isEqualTo(1);
      assertThat(graph.nodes()).hasSizeGreaterThan(40);
   }

   @Test
   void контейнерыРядаСчитаютсяИзПрямоугольника() {
      RowShape row14 = RowShape.parse(rect(236, 626, 289, 52), RowType.ROW);
      // бокс 12 из 15 на северной стороне — там же, где в макете экрана 18 (x ≈ 470)
      Point box12 = row14.containerCenter(Side.NORTH, 12, 15);
      assertThat(box12.x()).isCloseTo(457.5, within(1.0));
      assertThat(box12.y()).isCloseTo(639, within(0.5));
      assertThat(row14.containerDoor(Side.NORTH, 12, 15).y()).isCloseTo(626, within(0.5));
      assertThat(row14.containerDoor(Side.SOUTH, 1, 15).y()).isCloseTo(678, within(0.5));
      assertThat(row14.angleAt(1, 15)).isEqualTo(0);

      RowShape rowYa = RowShape.parse(rect(658, 95, 54, 535), RowType.VROW);
      assertThat(rowYa.containerDoor(Side.WEST, 1, 28).x()).isCloseTo(658, within(0.5));
      assertThat(rowYa.containerDoor(Side.EAST, 1, 28).x()).isCloseTo(712, within(0.5));
      assertThat(rowYa.angleAt(1, 28)).isEqualTo(90);
   }

   @Test
   void маршрутКакВМакете18() {
      RowShape row14 = RowShape.parse(rect(236, 626, 289, 52), RowType.ROW);
      Point door = row14.containerDoor(Side.NORTH, 12, 15);
      Point center = row14.containerCenter(Side.NORTH, 12, 15);

      // «я здесь» на центральном проходе, как точка в макете (584, 540); текст шагов — как на экране 18
      Router.Path path = new Router(graph).route(new Point(584, 540), door);
      List<RouteSteps.Step> steps = RouteSteps.build(path, names, center, 12, METERS_PER_PX, Lang.RU);

      assertThat(steps).extracting(RouteSteps.Step::text).containsExactly(
            "Прямо по центральному проходу ~20 м",
            "Направо — между рядами 16 и 14",
            "Через ~30 м бокс 12 по левую руку");
      assertThat(steps).extracting(RouteSteps.Step::kind).containsExactly(
            RouteSteps.StepKind.STRAIGHT, RouteSteps.StepKind.RIGHT, RouteSteps.StepKind.ARRIVE);
      // полилиния идёт по проходам и заканчивается у двери контейнера
      List<Point> polyline = path.polyline();
      assertThat(polyline.get(0)).isEqualTo(new Point(584, 540));
      assertThat(polyline.get(polyline.size() - 1).distance(new Point(door.x(), 615))).isLessThan(1);
      assertThat(path.length() * METERS_PER_PX).isBetween(45.0, 55.0);

      List<RouteSteps.Step> kg = RouteSteps.build(path, names, center, 12, METERS_PER_PX, Lang.KG);
      assertThat(kg.get(0).text()).isEqualTo("Борбордук өтмөк менен түз ~20 м");
      assertThat(kg.get(1).text()).isEqualTo("Оңго — 16 жана 14 катарлардын ортосуна");
      assertThat(kg.get(2).text()).isEqualTo("~30 м өткөндөн кийин 12-бокс сол жагыңызда");
   }

   @Test
   void маршрутОтГлавногоВходаКВосточнымРядам() {
      // ряд Ц: блок восточнее — связан с центральным проходом только «мостиками»
      RowShape rowC = RowShape.parse(rect(742, 652, 393, 47), RowType.ROW);
      Router.Path path = new Router(graph).route(new Point(584, 34), rowC.containerDoor(Side.NORTH, 5, 21));
      assertThat(path.legs()).isNotEmpty();
      assertThat(path.length()).isGreaterThan(600).isLessThan(2000);
   }

   @Test
   void рядомСКонтейнером() {
      RowShape row14 = RowShape.parse(rect(236, 626, 289, 52), RowType.ROW);
      Point door = row14.containerDoor(Side.NORTH, 12, 15);
      Router.Path path = new Router(graph).route(new Point(door.x(), 615), door);
      List<RouteSteps.Step> steps = RouteSteps.build(path, names, row14.containerCenter(Side.NORTH, 12, 15), 12,
            METERS_PER_PX, Lang.RU);
      assertThat(steps).hasSize(1);
      assertThat(steps.get(0).kind()).isEqualTo(RouteSteps.StepKind.ARRIVE);
   }

   private static JsonNode rect(double x, double y, double w, double h) {
      ObjectMapper mapper = new ObjectMapper();
      var node = mapper.createObjectNode();
      node.putObject("rect").put("x", x).put("y", y).put("w", w).put("h", h);
      return node;
   }
}
