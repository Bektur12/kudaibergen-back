package kg.kudaibergen.chat;

import java.util.Objects;

import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.entity.ShopMember;
import org.springframework.stereotype.Component;

/**
 * Кто пишет в чат: покупатель — сам, продавец — любой человек бокса (владелец или сотрудник, ТЗ 2).
 * Посторонним — 404, как будто чата нет.
 */
@Component
public class ChatAccess {

   private final ChatRepository chats;
   private final ShopMemberRepository members;

   public ChatAccess(ChatRepository chats, ShopMemberRepository members) {
      this.chats = chats;
      this.members = members;
   }

   public Participant require(Long userId, Long chatId) {
      return participant(userId, chats.findById(chatId).orElseThrow(ChatAccess::notFound));
   }

   /** То же с блокировкой строки чата — для отправки и отметки прочтения. */
   public Participant requireForUpdate(Long userId, Long chatId) {
      return participant(userId, chats.findForUpdate(chatId).orElseThrow(ChatAccess::notFound));
   }

   public Participant participant(Long userId, Chat chat) {
      if (chat.getBuyerId().equals(userId)) {
         return new Participant(chat, ChatSide.BUYER, userId);
      }
      boolean member = members.findByUserId(userId)
            .map(ShopMember::getShopId)
            .filter(shopId -> Objects.equals(shopId, chat.getShopId()))
            .isPresent();
      if (member) {
         return new Participant(chat, ChatSide.SHOP, userId);
      }
      throw notFound();
   }

   static NotFoundException notFound() {
      return new NotFoundException("CHAT_NOT_FOUND", "Чат не найден");
   }

   public record Participant(Chat chat, ChatSide side, Long userId) {
   }
}
