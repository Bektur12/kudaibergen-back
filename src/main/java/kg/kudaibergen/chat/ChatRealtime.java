package kg.kudaibergen.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kg.kudaibergen.chat.dto.ChatEvent;
import kg.kudaibergen.chat.dto.UnreadDto;
import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.chat.entity.Message;
import kg.kudaibergen.chat.realtime.CentrifugoClient;
import kg.kudaibergen.chat.realtime.CentrifugoClient.Publication;
import kg.kudaibergen.chat.realtime.ChatChannels;
import kg.kudaibergen.notification.push.PushMessage;
import kg.kudaibergen.notification.push.QuietHours;
import kg.kudaibergen.notification.push.UserPushes;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Доставка после коммита. В канал чата — само событие (MESSAGE, READ); в личные каналы inbox обеих
 * сторон — обновлённая строка списка чатов и бейдж непрочитанных. Пуш «имя + текст» получает только
 * тот, кто сейчас не смотрит этот чат (нет его presence в канале чата) и не выключил уведомления чата.
 */
@Component
public class ChatRealtime {

   private final ChatRepository chats;
   private final MessageRepository messages;
   private final ShopRepository shops;
   private final UserRepository users;
   private final ChatView view;
   private final CentrifugoClient centrifugo;
   private final UserPushes pushes;
   private final QuietHours quietHours;
   private final ChatProviders providers;

   public ChatRealtime(ChatRepository chats, MessageRepository messages, ShopRepository shops, UserRepository users,
                       ChatView view, CentrifugoClient centrifugo, UserPushes pushes, QuietHours quietHours,
                       ChatProviders providers) {
      this.chats = chats;
      this.messages = messages;
      this.shops = shops;
      this.users = users;
      this.view = view;
      this.centrifugo = centrifugo;
      this.pushes = pushes;
      this.quietHours = quietHours;
      this.providers = providers;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
   public void on(ChatEvents.MessageAdded event) {
      Chat chat = chats.findById(event.chatId()).orElseThrow();
      Message message = messages.findById(event.messageId()).orElseThrow();
      List<Publication> publications = new ArrayList<>();
      publications.add(new Publication(ChatChannels.chat(chat.getId()), ChatEvent.message(view.message(message, chat))));
      inboxes(chat, publications);
      centrifugo.publishAll(publications);
      if (event.push() && message.getSide() != ChatSide.SYSTEM) {
         push(chat, message);
      }
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
   public void on(ChatEvents.Read event) {
      Chat chat = chats.findById(event.chatId()).orElseThrow();
      List<Publication> publications = new ArrayList<>();
      publications.add(new Publication(ChatChannels.chat(chat.getId()),
            ChatEvent.read(new ChatEvent.ReadPayload(chat.getId(), event.side(), event.readMessageId()))));
      inboxes(chat, publications);
      centrifugo.publishAll(publications);
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
   public void on(ChatEvents.Changed event) {
      Chat chat = chats.findById(event.chatId()).orElseThrow();
      List<Publication> publications = new ArrayList<>();
      inboxes(chat, publications);
      centrifugo.publishAll(publications);
   }

   /** Строка списка и бейдж каждому: покупателю — с его стороны, людям исполнителя — с его стороны. */
   private void inboxes(Chat chat, List<Publication> publications) {
      List<Long> providerUsers = providers.userIds(chat);
      List<Long> everyone = new ArrayList<>(providerUsers);
      everyone.add(chat.getBuyerId());
      Map<Long, User> byId = new LinkedHashMap<>();
      users.findAllById(everyone).forEach(user -> byId.put(user.getId(), user));

      for (Long userId : everyone) {
         User user = byId.get(userId);
         if (user == null) {
            continue;
         }
         ChatSide side = userId.equals(chat.getBuyerId()) ? ChatSide.BUYER : ChatSide.SHOP;
         // пустой прямой чат исполнитель ещё не видит
         if (side == ChatSide.SHOP && chat.getLastMessageId() == null) {
            continue;
         }
         String inbox = ChatChannels.inbox(userId);
         Lang lang = user.getLang();
         publications.add(new Publication(inbox, ChatEvent.chat(view.rows(List.of(chat), side, lang).get(0))));
         publications.add(new Publication(inbox, ChatEvent.unread(unread(userId))));
      }
   }

   private UnreadDto unread(Long userId) {
      Long shopId = view.shopIdOf(userId);
      Long masterId = providers.masterIdOf(userId);
      return new UnreadDto(chats.totalUnreadForBuyer(userId), shopId == null ? 0 : chats.totalUnreadForShop(shopId),
            masterId == null ? 0 : chats.totalUnreadForMaster(masterId));
   }

   private void push(Chat chat, Message message) {
      List<Long> recipients = message.getSide() == ChatSide.BUYER
            ? providers.userIds(chat) : List.of(chat.getBuyerId());
      Set<Long> watching = centrifugo.presence(List.of(ChatChannels.chat(chat.getId())))
            .getOrDefault(ChatChannels.chat(chat.getId()), Set.of());
      List<Long> away = recipients.stream()
            .filter(id -> !watching.contains(id) && !id.equals(message.getSenderId()))
            .toList();
      if (away.isEmpty()) {
         return;
      }
      String providerName = providers.name(chat);
      User buyer = users.findById(chat.getBuyerId()).orElse(null);
      boolean quiet = quietHours.now();
      pushes.send(away, (user, settings) -> {
         if (!settings.isNotifyChat()) {
            return null;
         }
         String title = message.getSide() == ChatSide.SHOP ? providerName
               : buyer == null || buyer.getName() == null ? ChatTexts.buyer(user.getLang()) : buyer.getName();
         return new PushMessage(title, ChatTexts.preview(message, user.getLang()),
               Map.of("type", "CHAT_MESSAGE", "chatId", chat.getId().toString(),
                     "messageId", message.getId().toString()),
               null, !quiet);
      });
   }
}
