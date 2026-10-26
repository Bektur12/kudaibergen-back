package kg.kudaibergen.delivery.entity;

/**
 * Куда везти — плитки «КУДА ВЕЗТИ» в порядке сетки. inName — «Адрес в Оше», «Доставка в Оше»; city — строка
 * «Район / населённый пункт» в заявке Ылдам. remote — 2 дня и тариф отдалённых (Баткенская область).
 */
public enum DeliveryRegion {
   BISHKEK("Бишкек", "Бишкеке", "г. Бишкек", false),
   CHUY("Чуйская обл.", "Чуйской обл.", "Чуйская обл.", false),
   OSH("Ош", "Оше", "г. Ош", false),
   JALAL_ABAD("Джалал-Абад", "Джалал-Абаде", "г. Джалал-Абад", false),
   ISSYK_KUL("Иссык-Куль", "Иссык-Куле", "Иссык-Кульская обл.", false),
   NARYN("Нарын", "Нарыне", "г. Нарын", false),
   TALAS("Талас", "Таласе", "г. Талас", false),
   BATKEN("Баткен", "Баткене", "г. Баткен", true);

   private final String title;
   private final String inName;
   private final String city;
   private final boolean remote;

   DeliveryRegion(String title, String inName, String city, boolean remote) {
      this.title = title;
      this.inName = inName;
      this.city = city;
      this.remote = remote;
   }

   public String title() {
      return title;
   }

   public String inName() {
      return inName;
   }

   public String city() {
      return city;
   }

   public boolean remote() {
      return remote;
   }
}
