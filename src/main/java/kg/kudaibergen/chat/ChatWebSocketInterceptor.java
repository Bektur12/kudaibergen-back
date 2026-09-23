package kg.kudaibergen.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kg.kudaibergen.auth.JwtService;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * CONNECT: разбирает JWT из STOMP-заголовка Authorization (обычный HTTP-заголовок при
 * WebSocket handshake браузер не даёт выставить, поэтому токен едет внутри STOMP-фрейма) и
 * кладёт AuthPrincipal в атрибуты сессии.
 * SUBSCRIBE на /topic/chats/{id}(/typing|/presence|/read) и SEND на /app/chats/{id}/typing:
 * проверяет, что подключившийся — участник этого чата, иначе чужой чат ни читать,
 * ни слать туда typing нельзя.
 * SUBSCRIBE на /topic/users/{id}/chats (живой инбокс): проверяет, что id — это сам
 * подключившийся, чужой инбокс слушать нельзя.
 */
@Component
public class ChatWebSocketInterceptor implements ChannelInterceptor {

   private static final Logger log = LoggerFactory.getLogger(ChatWebSocketInterceptor.class);
   private static final String PREFIX = "Bearer ";
   private static final Pattern SUBSCRIBE_CHAT_TOPIC =
         Pattern.compile("^/topic/chats/(\\d+)(?:/(?:typing|presence|read))?$");
   private static final Pattern SUBSCRIBE_USER_TOPIC = Pattern.compile("^/topic/users/(\\d+)/chats$");
   private static final Pattern SEND_TOPIC = Pattern.compile("^/app/chats/(\\d+)/typing$");
   static final String PRINCIPAL_ATTR = "authPrincipal";

   private final JwtService jwtService;
   private final ChatService chatService;

   public ChatWebSocketInterceptor(JwtService jwtService, @Lazy ChatService chatService) {
      this.jwtService = jwtService;
      this.chatService = chatService;
   }

   @Override
   public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
      StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
      if (accessor == null) {
         return message;
      }
      if (StompCommand.CONNECT.equals(accessor.getCommand())) {
         accessor.getSessionAttributes().put(PRINCIPAL_ATTR, authenticateLogged(accessor));
      } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
         authorizeChatDestination(accessor, SUBSCRIBE_CHAT_TOPIC);
         authorizeOwnUserDestination(accessor, SUBSCRIBE_USER_TOPIC);
      } else if (StompCommand.SEND.equals(accessor.getCommand())) {
         authorizeChatDestination(accessor, SEND_TOPIC);
      }
      return message;
   }

   /** Логирует причину отказа на CONNECT — иначе видна только агрегированная статистика брокера. */
   private AuthPrincipal authenticateLogged(StompHeaderAccessor accessor) {
      try {
         return authenticate(accessor);
      } catch (RuntimeException ex) {
         log.warn("WS CONNECT отклонён: {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
         throw ex;
      }
   }

   private AuthPrincipal authenticate(StompHeaderAccessor accessor) {
      String header = accessor.getFirstNativeHeader("Authorization");
      if (header == null || !header.startsWith(PREFIX)) {
         throw new IllegalArgumentException("Нужен заголовок Authorization: Bearer <token>");
      }
      JwtService.ParsedToken parsed = jwtService.parseAccessToken(header.substring(PREFIX.length()).trim());
      return new AuthPrincipal(parsed.userId(), parsed.phone(), parsed.role());
   }

   private void authorizeChatDestination(StompHeaderAccessor accessor, Pattern pattern) {
      Matcher matcher = pattern.matcher(String.valueOf(accessor.getDestination()));
      if (!matcher.matches()) {
         return;
      }
      chatService.assertParticipant(Long.valueOf(matcher.group(1)), principalOf(accessor).userId());
   }

   /** Инбокс — не про членство в чате, а про то, что id в топике — это сам подключившийся. */
   private void authorizeOwnUserDestination(StompHeaderAccessor accessor, Pattern pattern) {
      Matcher matcher = pattern.matcher(String.valueOf(accessor.getDestination()));
      if (!matcher.matches()) {
         return;
      }
      Long ownerId = Long.valueOf(matcher.group(1));
      if (!ownerId.equals(principalOf(accessor).userId())) {
         throw new IllegalStateException("Нельзя слушать чужой инбокс");
      }
   }

   private AuthPrincipal principalOf(StompHeaderAccessor accessor) {
      Object principal = accessor.getSessionAttributes() == null
            ? null : accessor.getSessionAttributes().get(PRINCIPAL_ATTR);
      if (!(principal instanceof AuthPrincipal authPrincipal)) {
         throw new IllegalStateException("Нет авторизации на сокете");
      }
      return authPrincipal;
   }
}
