package kg.kudaibergen.chat;

import kg.kudaibergen.chat.entity.Chat;
import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.common.error.NotFoundException;
import org.springframework.stereotype.Component;

/**
 * Кто пишет в чат: покупатель — сам, исполнитель — любой человек бокса (владелец или сотрудник, ТЗ 2)
 * или мастер. Посторонним — 404, как будто чата нет.
 */
@Component
public class ChatAccess {

   private final ChatRepository chats;
   private final ChatProviders providers;

   public ChatAccess(ChatRepository chats, ChatProviders providers) {
      this.chats = chats;
      this.providers = providers;
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
      if (providers.isProviderUser(userId, chat)) {
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
