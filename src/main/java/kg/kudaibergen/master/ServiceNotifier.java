package kg.kudaibergen.master;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.common.config.ClockConfig;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.master.entity.ServiceOffer;
import kg.kudaibergen.master.entity.ServiceRequest;
import kg.kudaibergen.master.entity.ServiceType;
import kg.kudaibergen.notification.push.PushMessage;
import kg.kudaibergen.notification.push.QuietHours;
import kg.kudaibergen.notification.push.UserPushes;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Пуши по заявкам на услуги, на языке получателя. Мастеру — новая заявка (звук — по «Звуку нового запроса»),
 * клиенту — «могу помочь» и «время вышло» (по «Уведомлениям об ответах»), мастеру — «договорились».
 * data.type: NEW_SERVICE_REQUEST → 39, SERVICE_OFFER → 37, SERVICE_NO_OFFERS → 37 с «Расширить радиус»,
 * SERVICE_EXPIRED → 37 с «Продлить», SERVICE_DEAL → 39.
 */
@Component
public class ServiceNotifier {

   static final String NEW_SERVICE_REQUEST_CATEGORY = "NEW_SERVICE_REQUEST";
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
   private static final ZoneId ZONE = ClockConfig.MARKET_ZONE;

   private final ServiceRequestRepository requests;
   private final ServiceOfferRepository offers;
   private final ServiceRecipientRepository recipients;
   private final MasterRepository masters;
   private final ServiceCatalog catalog;
   private final ServiceRequestMapper mapper;
   private final UserPushes pushes;
   private final QuietHours quietHours;

   public ServiceNotifier(ServiceRequestRepository requests, ServiceOfferRepository offers,
                          ServiceRecipientRepository recipients, MasterRepository masters, ServiceCatalog catalog,
                          ServiceRequestMapper mapper, UserPushes pushes, QuietHours quietHours) {
      this.requests = requests;
      this.offers = offers;
      this.recipients = recipients;
      this.masters = masters;
      this.catalog = catalog;
      this.mapper = mapper;
      this.pushes = pushes;
      this.quietHours = quietHours;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(ServiceEvents.ServiceDispatched event) {
      if (event.masterIds().isEmpty()) {
         return;
      }
      ServiceRequest request = requests.findById(event.requestId()).orElseThrow();
      ServiceType type = catalog.require(request.getService());
      String car = mapper.car(request).label();
      Map<Long, Long> masterOfOwner = masters.findAllById(event.masterIds()).stream()
            .collect(Collectors.toMap(Master::getOwnerId, Master::getId));
      Map<Long, Integer> distance = recipients.findByRequestIdAndMasterIdIn(request.getId(), event.masterIds()).stream()
            .collect(Collectors.toMap(r -> r.getMasterId(), r -> r.getDistanceM()));
      boolean quiet = quietHours.now();
      pushes.send(masterOfOwner.keySet(), (user, settings) -> {
         Long masterId = masterOfOwner.get(user.getId());
         String title = (type.isUrgent() ? "⚡ " : "") + (user.getLang() == Lang.KG ? "Жаңы заявка: " : "Новая заявка: ")
               + type.name(user.getLang());
         String body = car + " · " + km(distance.getOrDefault(masterId, 0)) + " — " + shorten(request.getDescription());
         return new PushMessage(title, body, Map.of("type", "NEW_SERVICE_REQUEST", "requestId",
               request.getId().toString(), "masterId", masterId.toString()), NEW_SERVICE_REQUEST_CATEGORY,
               !quiet && settings.isNewRequestSound());
      });
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(ServiceEvents.ServiceOfferReceived event) {
      ServiceRequest request = requests.findById(event.requestId()).orElseThrow();
      ServiceOffer offer = offers.findById(event.offerId()).orElseThrow();
      Master master = masters.findById(offer.getMasterId()).orElseThrow();
      toBuyer(request, user -> new PushMessage(
            master.getName() + (user.getLang() == Lang.KG ? ": жардам бере алам" : ": могу помочь"),
            offerBody(offer, user.getLang()),
            Map.of("type", "SERVICE_OFFER", "requestId", request.getId().toString(), "offerId", offer.getId().toString(),
                  "masterId", master.getId().toString()), null, !quietHours.now()));
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(ServiceEvents.ServiceExpired event) {
      ServiceRequest request = requests.findById(event.requestId()).orElseThrow();
      int count = event.canHelpCount();
      toBuyer(request, user -> {
         boolean kg = user.getLang() == Lang.KG;
         String service = catalog.require(request.getService()).name(user.getLang());
         String title = count == 0 ? (kg ? "Азырынча эч ким жооп берген жок" : "Пока никто не откликнулся")
               : (kg ? "Убакыт бүттү: " + count + " жооп" : "Время вышло: " + count + " " + offersWord(count));
         String body = count == 0 ? "«" + service + "» — " + (kg ? "радиусту кеңейтесизби?" : "расширить радиус?")
               : "«" + service + "» — " + (kg ? "узартасызбы?" : "продлить?");
         return new PushMessage(title, body, Map.of("type", count == 0 ? "SERVICE_NO_OFFERS" : "SERVICE_EXPIRED",
               "requestId", request.getId().toString()), null, !quietHours.now());
      });
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(ServiceEvents.ServiceClosed event) {
      if (event.masterId() == null) {
         return;
      }
      Master master = masters.findById(event.masterId()).orElseThrow();
      boolean quiet = quietHours.now();
      pushes.send(List.of(master.getOwnerId()), (user, settings) -> new PushMessage(
            (user.getLang() == Lang.KG ? "Кардар сизди тандады" : "Клиент выбрал вас")
                  + (event.stars() == null ? "" : " · ★ " + event.stars()),
            null, Map.of("type", "SERVICE_DEAL", "requestId", event.requestId().toString()), null, !quiet));
   }

   /** «от 1 500 сом · сегодня 15:00 — «Подъеду с домкратом»». */
   static String offerBody(ServiceOffer offer, Lang lang) {
      StringBuilder body = new StringBuilder();
      if (offer.getPriceFrom() != null) {
         body.append(lang == Lang.KG ? "" : "от ").append(NumberFormat.getIntegerInstance(new Locale("ru"))
               .format(offer.getPriceFrom()).replace(' ', ' ')).append(lang == Lang.KG ? " сомдон" : " сом");
      }
      if (offer.getAvailableAt() != null) {
         if (!body.isEmpty()) {
            body.append(" · ");
         }
         body.append(offer.getAvailableAt().atZone(ZONE).format(TIME));
      }
      if (offer.getMessage() != null) {
         body.append(body.isEmpty() ? "" : " — ").append("«").append(offer.getMessage()).append("»");
      }
      return body.isEmpty() ? null : body.toString();
   }

   static String km(int meters) {
      return meters < 1000 ? meters + " м" : String.format(Locale.ROOT, "%.1f км", meters / 1000.0).replace('.', ',');
   }

   /** 1 отклик, 2 отклика, 5 откликов. */
   static String offersWord(int count) {
      int mod100 = count % 100;
      int mod10 = count % 10;
      if (mod100 >= 11 && mod100 <= 14) {
         return "откликов";
      }
      return mod10 == 1 ? "отклик" : mod10 >= 2 && mod10 <= 4 ? "отклика" : "откликов";
   }

   private static String shorten(String text) {
      return text.length() <= 80 ? text : text.substring(0, 79) + "…";
   }

   private void toBuyer(ServiceRequest request, Function<User, PushMessage> message) {
      pushes.send(List.of(request.getBuyerId()),
            (user, settings) -> settings.isNotifyReplies() ? message.apply(user) : null);
   }
}
