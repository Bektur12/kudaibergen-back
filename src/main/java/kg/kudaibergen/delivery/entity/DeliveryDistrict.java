package kg.kudaibergen.delivery.entity;

/**
 * Отдалённые районы — чипы под областью: 2 дня и тариф отдалённых. city — строка «Район / населённый пункт»
 * в заявке Ылдам.
 */
public enum DeliveryDistrict {
   CHON_ALAY(DeliveryRegion.OSH, "Чон-Алай", "Чон-Алайский р-н, Ошская обл."),
   ALAY(DeliveryRegion.OSH, "Алай (Гульчо)", "Алайский р-н (Гульчо), Ошская обл."),
   AKSY(DeliveryRegion.JALAL_ABAD, "Аксы (Кербен)", "Аксыйский р-н (Кербен), Джалал-Абадская обл."),
   ALA_BUKA(DeliveryRegion.JALAL_ABAD, "Ала-Бука", "Ала-Букинский р-н, Джалал-Абадская обл.");

   private final DeliveryRegion region;
   private final String title;
   private final String city;

   DeliveryDistrict(DeliveryRegion region, String title, String city) {
      this.region = region;
      this.title = title;
      this.city = city;
   }

   public DeliveryRegion region() {
      return region;
   }

   public String title() {
      return title;
   }

   public String city() {
      return city;
   }
}
