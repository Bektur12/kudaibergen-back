package kg.kudaibergen.request;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.chat.ChatRepository;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.MarketSnapshot;
import kg.kudaibergen.request.dto.RequestStatsDto;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.request.entity.RecipientStatus;
import kg.kudaibergen.request.entity.ReplyAnswer;
import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestReply;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import org.springframework.stereotype.Component;

/**
 * Статистика запроса для покупателя (32): сколько получили, открыли, ответили «Есть» / «Нет», молчат;
 * кто ответил «Есть» (магазин, место, цена, чат) и где ответили «Нет» (только ряд и контейнер — без названия).
 * Вызывается внутри транзакции: из REST и из живого обновления.
 */
@Component
public class RequestStatsView {

   private final RequestRecipientRepository recipients;
   private final RequestReplyRepository replies;
   private final ShopRepository shops;
   private final ShopMapper shopMapper;
   private final ChatRepository chats;
   private final MarketMapService market;
   private final Clock clock;

   public RequestStatsView(RequestRecipientRepository recipients, RequestReplyRepository replies,
                           ShopRepository shops, ShopMapper shopMapper, ChatRepository chats, MarketMapService market,
                           Clock clock) {
      this.recipients = recipients;
      this.replies = replies;
      this.shops = shops;
      this.shopMapper = shopMapper;
      this.chats = chats;
      this.market = market;
      this.clock = clock;
   }

   public RequestStatsDto build(PartRequest request) {
      Long requestId = request.getId();
      RequestRecipientRepository.Counts counts = recipients.counts(requestId);
      long silent = Math.max(0, counts.getDelivered() - counts.getHave() - counts.getNotHave());

      List<RequestRecipient> have = recipients.findByRequestIdAndStatusOrderByRepliedAtAsc(requestId,
            RecipientStatus.HAVE);
      List<RequestRecipient> notHave = recipients.findByRequestIdAndStatusOrderByRepliedAtAsc(requestId,
            RecipientStatus.NOT_HAVE);
      Map<Long, RequestReply> replyOfShop = replies.findHave(requestId, ReplyAnswer.HAVE, 0).stream()
            .collect(Collectors.toMap(RequestReply::getShopId, Function.identity()));
      Map<Long, Shop> shopsById = shops.findAllById(have.stream().map(RequestRecipient::getShopId).toList())
            .stream().collect(Collectors.toMap(Shop::getId, Function.identity()));
      Map<Long, Long> chatOfShop = have.isEmpty() ? Map.of() : chats.findByRequestId(requestId).stream()
            .collect(Collectors.toMap(Chat::getShopId, Chat::getId, (a, b) -> a));
      MarketSnapshot snapshot = market.snapshot();

      List<RequestStatsDto.Have> haveItems = have.stream()
            .filter(recipient -> shopsById.containsKey(recipient.getShopId()))
            .map(recipient -> {
               RequestReply reply = replyOfShop.get(recipient.getShopId());
               return new RequestStatsDto.Have(shopMapper.card(shopsById.get(recipient.getShopId())),
                     rowCode(snapshot, recipient), containerNumber(snapshot, recipient), recipient.getRepliedAt(),
                     reply == null ? null : reply.getPrice(), chatOfShop.get(recipient.getShopId()));
            })
            .toList();
      List<RequestStatsDto.Place> notHaveItems = notHave.stream()
            .map(recipient -> new RequestStatsDto.Place(rowCode(snapshot, recipient),
                  containerNumber(snapshot, recipient)))
            .toList();

      Instant now = clock.instant();
      long durationMin = Duration.between(request.getSentAt(), request.getExpiresAt()).toMinutes();
      long remainingSec = request.isActive() ? Math.max(0, Duration.between(now, request.getExpiresAt()).toSeconds()) : 0;
      return new RequestStatsDto(requestId, request.getStatus(), request.getExpiresAt(), durationMin,
            (remainingSec + 59) / 60, request.canExtend(), request.getExtendedTimes(),
            new RequestStatsDto.Counts(counts.getDelivered(), counts.getSeen(), counts.getHave(),
                  counts.getNotHave(), silent),
            haveItems, notHaveItems);
   }

   private static String rowCode(MarketSnapshot snapshot, RequestRecipient recipient) {
      return recipient.getRowId() == null ? null
            : snapshot.row(recipient.getRowId()).map(row -> row.row().getCode()).orElse(null);
   }

   private static Integer containerNumber(MarketSnapshot snapshot, RequestRecipient recipient) {
      return recipient.getContainerId() == null ? null
            : snapshot.container(recipient.getContainerId()).map(view -> (int) view.container().getNumber())
            .orElse(null);
   }
}
