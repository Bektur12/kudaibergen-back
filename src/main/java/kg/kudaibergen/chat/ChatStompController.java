package kg.kudaibergen.chat;

import kg.kudaibergen.chat.dto.TypingRequest;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * Единственное, что клиент шлёт серверу через STOMP (не HTTP) — событие «печатает».
 * Авторизация (участник ли отправитель этого чата) уже сделана в ChatWebSocketInterceptor
 * на уровне SEND /app/chats/{id}/typing, здесь только достаём отправителя и ретранслируем.
 */
@Controller
public class ChatStompController {

   private final ChatService chatService;

   public ChatStompController(ChatService chatService) {
      this.chatService = chatService;
   }

   @MessageMapping("/chats/{chatId}/typing")
   public void typing(@DestinationVariable Long chatId, @Payload TypingRequest request,
                      SimpMessageHeaderAccessor accessor) {
      Object attr = accessor.getSessionAttributes() == null
            ? null : accessor.getSessionAttributes().get(ChatWebSocketInterceptor.PRINCIPAL_ATTR);
      if (attr instanceof AuthPrincipal principal) {
         chatService.broadcastTyping(chatId, principal.userId(), request.typing());
      }
   }
}
