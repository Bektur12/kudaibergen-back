package kg.kudaibergen.market.route;

import java.util.ArrayList;
import java.util.List;

import kg.kudaibergen.market.geo.Point;
import kg.kudaibergen.user.entity.Lang;

/**
 * Шаги маршрута текстом (экран 18):
 * <ol>
 *    <li>«Прямо по центральному проходу ~15 м»</li>
 *    <li>«Направо — между рядами 16 и 14»</li>
 *    <li>«Через ~35 м бокс 12 по левую руку»</li>
 * </ol>
 * Короткие куски (стыки, мостики) вливаются в соседние участки, повороты меньше 30° считаются «прямо».
 */
public final class RouteSteps {

   /** Участок короче ~3 м — не отдельный шаг. */
   static final double MIN_LEG_METERS = 3;
   private static final double STRAIGHT_DEGREES = 30;

   public enum Kind {
      STRAIGHT, LEFT, RIGHT, ARRIVE
   }

   public record Step(int n, Kind kind, String text, int distanceM) {
   }

   private RouteSteps() {
   }

   /**
    * @param names          имена проходов по индексу
    * @param container      центр контейнера — чтобы понять, по какую руку он будет
    * @param containerNumber номер бокса для последнего шага
    */
   public static List<Step> build(Router.Path path, List<PassageName> names, Point container, int containerNumber,
                                  double metersPerPx, Lang lang) {
      List<Router.Leg> legs = mergeShort(path.legs(), metersPerPx);
      List<Step> steps = new ArrayList<>();
      if (legs.isEmpty()) {
         steps.add(new Step(1, Kind.ARRIVE, arrive(lang, containerNumber, 0, true), 0));
         return steps;
      }

      for (int i = 0; i < legs.size(); i++) {
         Router.Leg leg = legs.get(i);
         PassageName name = name(names, leg.passage());
         boolean lastLeg = i == legs.size() - 1;
         int meters = meters(leg.length(), metersPerPx);
         if (i == 0) {
            String text = lastLeg ? straight(lang, name, null) : straight(lang, name, meters);
            steps.add(new Step(steps.size() + 1, Kind.STRAIGHT, text, lastLeg ? 0 : meters));
         } else {
            Kind turn = turn(legs.get(i - 1).endDirection(), leg.startDirection());
            String text = turnText(lang, turn, name, lastLeg ? null : meters);
            steps.add(new Step(steps.size() + 1, turn, text, lastLeg ? 0 : meters));
         }
      }

      Router.Leg last = legs.get(legs.size() - 1);
      boolean rightHand = last.endDirection().cross(container.minus(path.finish())) > 0;
      int lastMeters = meters(last.length(), metersPerPx);
      steps.add(new Step(steps.size() + 1, Kind.ARRIVE, arrive(lang, containerNumber, lastMeters, rightHand),
            lastMeters));
      return steps;
   }

   /** Округление до 5 м, не меньше 5. */
   public static int meters(double px, double metersPerPx) {
      return (int) Math.max(5, Math.round(px * metersPerPx / 5.0) * 5);
   }

   static Kind turn(Point before, Point after) {
      double angle = Math.toDegrees(Math.atan2(before.cross(after), before.dot(after)));
      if (Math.abs(angle) < STRAIGHT_DEGREES) {
         return Kind.STRAIGHT;
      }
      return angle > 0 ? Kind.RIGHT : Kind.LEFT;
   }

   private static List<Router.Leg> mergeShort(List<Router.Leg> legs, double metersPerPx) {
      List<Router.Leg> merged = new ArrayList<>();
      for (Router.Leg leg : legs) {
         boolean tiny = leg.length() * metersPerPx < MIN_LEG_METERS || leg.passage() == PassageGraph.BRIDGE;
         if (tiny && !merged.isEmpty()) {
            Router.Leg previous = merged.remove(merged.size() - 1);
            merged.add(join(previous, leg, previous.passage()));
         } else if (!merged.isEmpty() && merged.get(merged.size() - 1).passage() == leg.passage()) {
            Router.Leg previous = merged.remove(merged.size() - 1);
            merged.add(join(previous, leg, leg.passage()));
         } else {
            merged.add(leg);
         }
      }
      // крошечный первый участок (сошли с места на проход) вливаем в следующий
      if (merged.size() > 1 && (merged.get(0).length() * metersPerPx < MIN_LEG_METERS
            || merged.get(0).passage() == PassageGraph.BRIDGE)) {
         Router.Leg first = merged.remove(0);
         Router.Leg second = merged.remove(0);
         merged.add(0, join(first, second, second.passage()));
      }
      // и вообще весь маршрут в пару метров — это «вы на месте»
      if (merged.size() == 1 && merged.get(0).length() * metersPerPx < MIN_LEG_METERS) {
         merged.clear();
      }
      return merged;
   }

   private static Router.Leg join(Router.Leg first, Router.Leg second, int passage) {
      List<Point> points = new ArrayList<>(first.points());
      for (Point point : second.points()) {
         if (points.get(points.size() - 1).distance(point) > 1e-6) {
            points.add(point);
         }
      }
      return new Router.Leg(passage, points);
   }

   private static PassageName name(List<PassageName> names, int passage) {
      return passage >= 0 && passage < names.size() ? names.get(passage) : PassageName.UNKNOWN;
   }

   private static String straight(Lang lang, PassageName name, Integer meters) {
      String distance = meters == null ? "" : " ~" + meters + " м";
      return switch (lang) {
         case RU -> "Прямо " + name.along(lang) + distance;
         case KG -> capitalize(name.along(lang)) + " түз" + distance;
      };
   }

   private static String turnText(Lang lang, Kind turn, PassageName name, Integer meters) {
      String direction = switch (lang) {
         case RU -> switch (turn) {
            case LEFT -> "Налево";
            case RIGHT -> "Направо";
            default -> "Прямо";
         };
         case KG -> switch (turn) {
            case LEFT -> "Солго";
            case RIGHT -> "Оңго";
            default -> "Түз";
         };
      };
      String distance = meters == null ? "" : ", ~" + meters + " м";
      return direction + " — " + name.into(lang) + distance;
   }

   private static String arrive(Lang lang, int number, int meters, boolean rightHand) {
      boolean near = meters == 0;
      return switch (lang) {
         case RU -> (near ? "Бокс " : "Через ~" + meters + " м бокс ") + number
               + (rightHand ? " по правую руку" : " по левую руку");
         case KG -> (near ? "" : "~" + meters + " м өткөндөн кийин ") + number + "-бокс"
               + (rightHand ? " оң жагыңызда" : " сол жагыңызда");
      };
   }

   private static String capitalize(String text) {
      return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
   }
}
