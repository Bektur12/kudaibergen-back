package kg.kudaibergen.chat;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Живой чат: STOMP over WebSocket (нативно в Spring, без Socket.IO — у него свой протокол,
 * и Spring его не поддерживает). Клиент подключается на /ws, шлёт JWT в заголовке CONNECT
 * (см. ChatWebSocketInterceptor), подписывается на /topic/chats/{id} и получает новые
 * сообщения, как только их сохраняет POST /chats/{id}/messages(/media) — сокет тут только
 * для push, вся бизнес-логика и валидация остаются в HTTP-эндпоинтах. Исключение — typing:
 * это чисто ephemeral событие без HTTP-аналога, поэтому клиент шлёт его сам через
 * /app/chats/{id}/typing (см. ChatStompController).
 */
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

   private final ChatWebSocketInterceptor interceptor;

   public ChatWebSocketConfig(ChatWebSocketInterceptor interceptor) {
      this.interceptor = interceptor;
   }

   @Override
   public void registerStompEndpoints(StompEndpointRegistry registry) {
      registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
      registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
   }

   /**
    * Без heartbeat сервер по умолчанию отвечает на CONNECT заголовком heart-beat:0,0 —
    * это выключает heartbeat в обе стороны на уровне STOMP-согласования, даже если клиент
    * его просит. В итоге ни сервер, ни клиент не замечают, что соединение тихо умерло
    * (типично для мобильных сетей — Wi-Fi/энергосбережение, смена сети), и push перестаёт
    * доходить до реконнекта руками. 10с/10с — стандартный интервал, совпадает с дефолтом
    * @stomp/stompjs.
    */
   @Override
   public void configureMessageBroker(MessageBrokerRegistry registry) {
      registry.enableSimpleBroker("/topic")
            .setHeartbeatValue(new long[] { 10000, 10000 })
            .setTaskScheduler(heartbeatScheduler());
      registry.setApplicationDestinationPrefixes("/app");
   }

   @Bean
   public TaskScheduler heartbeatScheduler() {
      ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
      scheduler.setPoolSize(1);
      scheduler.setThreadNamePrefix("ws-heartbeat-");
      scheduler.initialize();
      return scheduler;
   }

   @Override
   public void configureClientInboundChannel(ChannelRegistration registration) {
      registration.interceptors(interceptor);
   }
}
