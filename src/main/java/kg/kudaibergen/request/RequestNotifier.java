package kg.kudaibergen.request;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.VoiceDto;
import kg.kudaibergen.notification.push.PushMessage;
import kg.kudaibergen.notification.push.QuietHours;
import kg.kudaibergen.notification.push.UserPushes;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.request.entity.RequestReply;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopMember;
import kg.kudaibergen.user.entity.User;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Пуши по запросам (ТЗ 11.3): каждому получателю на его языке. Новые запросы — всем людям бокса
 * (владелец и сотрудники) с кнопками «Есть / Нет»; звук — по настройке «Звук нового запроса».
 * С 22:00 до 07:00 по Бишкеку все пуши приходят без звука. «Уведомления об ответах» выключены —
 * покупатель не получает пушей об ответах и таймере.
 */
@Component
public class RequestNotifier {

   static final String NEW_REQUEST_CATEGORY = "NEW_REQUEST";

   private final PartRequestRepository requests;
   private final RequestReplyRepository replies;
   private final ShopRepository shops;
   private final ShopMemberRepository members;
   private final ShopMapper shopMapper;
   private final RequestMapper mapper;
   private final UserPushes pushes;
   private final QuietHours quietHours;
   private final MediaService media;

   public RequestNotifier(PartRequestRepository requests, RequestReplyRepository replies, ShopRepository shops,
                          ShopMemberRepository members, ShopMapper shopMapper, RequestMapper mapper,
                          UserPushes pushes, QuietHours quietHours, MediaService media) {
      this.requests = requests;
      this.replies = replies;
      this.shops = shops;
      this.members = members;
      this.shopMapper = shopMapper;
      this.mapper = mapper;
      this.pushes = pushes;
      this.quietHours = quietHours;
      this.media = media;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(RequestEvents.Dispatched event) {
      if (event.shopIds().isEmpty()) {
         return;
      }
      PartRequest request = requests.findById(event.requestId()).orElseThrow();
      String carLabel = mapper.car(request).label();
      VoiceDto voice = media.voice(request.getVoiceMediaId());
      String photo = request.getPhotoIds().isEmpty() ? null : media.thumbUrl(request.getPhotoIds().get(0));
      Map<String, String> data = new HashMap<>(Map.of("type", "NEW_REQUEST", "requestId", request.getId().toString()));
      if (voice != null) {
         data.put("voiceUrl", voice.url());
         data.put("voiceDurationSec", String.valueOf(voice.durationSec()));
      }
      if (photo != null) {
         data.put("photoUrl", photo);
      }
      List<ShopMember> staff = members.findByShopIdIn(event.shopIds());
      Map<Long, Long> shopOfUser = staff.stream()
            .collect(Collectors.toMap(ShopMember::getUserId, ShopMember::getShopId));
      boolean quiet = quietHours.now();
      // пуш как на экране 12: машина заголовком, что нужно — текстом, фото и длина голосового — в data
      pushes.send(shopOfUser.keySet(), (user, settings) -> {
         Map<String, String> personal = new HashMap<>(data);
         personal.put("shopId", shopOfUser.get(user.getId()).toString());
         return new PushMessage(carLabel, request.getText(), personal, NEW_REQUEST_CATEGORY,
               !quiet && settings.isNewRequestSound());
      });
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(RequestEvents.HaveReceived event) {
      PartRequest request = requests.findById(event.requestId()).orElseThrow();
      RequestReply reply = replies.findById(event.replyId()).orElseThrow();
      Shop shop = shops.findById(reply.getShopId()).orElseThrow();
      LocationDto location = shopMapper.location(shop.getContainerId());
      toBuyer(request, user -> new PushMessage(
            RequestTexts.haveTitle(shop.getName(), user.getLang()),
            RequestTexts.haveBody(location.rowLabel(), location.number(), reply.getPrice()),
            Map.of("type", "REPLY_HAVE", "requestId", request.getId().toString(),
                  "replyId", reply.getId().toString(), "shopId", shop.getId().toString()),
            null, !quietHours.now()));
   }

   /**
    * Время вышло. Без «Есть» — «Пока никто не ответил» с предложением отправить всему рынку (20),
    * с ответами — «Время вышло: 3 ответа. Продлить?» (32).
    */
   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(RequestEvents.Expired event) {
      PartRequest request = requests.findById(event.requestId()).orElseThrow();
      int have = event.haveCount();
      toBuyer(request, user -> new PushMessage(
            have == 0 ? RequestTexts.noReplyTitle(user.getLang()) : RequestTexts.expiredTitle(have, user.getLang()),
            have == 0 ? RequestTexts.noReplyBody(request.getText(), user.getLang())
                  : RequestTexts.expiredBody(request.getText(), user.getLang()),
            Map.of("type", expiredPushType(have), "requestId", request.getId().toString(),
                  "haveCount", String.valueOf(have)),
            null, !quietHours.now()));
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(RequestEvents.Closed event) {
      if (event.shopId() == null) {
         return;
      }
      Set<Long> staff = members.findByShopIdIn(List.of(event.shopId())).stream()
            .map(ShopMember::getUserId).collect(Collectors.toSet());
      boolean quiet = quietHours.now();
      pushes.send(staff, (user, settings) -> new PushMessage(
            RequestTexts.saleTitle(event.stars(), user.getLang()), null,
            Map.of("type", "SALE", "requestId", event.requestId().toString()), null, !quiet));
   }

   /**
    * Куда ведёт пуш об истечении: NO_REPLY — экран 20 «Пока никто не ответил» («Отправить всему рынку» /
    * «Закрыть запрос»), REQUEST_EXPIRED — статистика запроса 32 с «Продлить».
    */
   static String expiredPushType(int haveCount) {
      return haveCount == 0 ? "NO_REPLY" : "REQUEST_EXPIRED";
   }

   /** «Уведомления об ответах» выключены — покупателю не шлём. */
   private void toBuyer(PartRequest request, Function<User, PushMessage> message) {
      pushes.send(List.of(request.getBuyerId()),
            (user, settings) -> settings.isNotifyReplies() ? message.apply(user) : null);
   }
}
