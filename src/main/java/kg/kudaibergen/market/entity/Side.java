package kg.kudaibergen.market.entity;

import kg.kudaibergen.market.geo.Point;

/**
 * Сторона ряда. У горизонтальных рядов — северная/южная (как в макете 10а),
 * у вертикальных — западная/восточная.
 */
public enum Side {
   NORTH(new Point(0, -1)),
   SOUTH(new Point(0, 1)),
   WEST(new Point(-1, 0)),
   EAST(new Point(1, 0));

   private final Point direction;

   Side(Point direction) {
      this.direction = direction;
   }

   /** Куда «смотрит» сторона на схеме (ось Y вниз). */
   public Point direction() {
      return direction;
   }

   public static Side[] of(RowType type) {
      return type == RowType.VROW ? new Side[] {WEST, EAST} : new Side[] {NORTH, SOUTH};
   }
}
