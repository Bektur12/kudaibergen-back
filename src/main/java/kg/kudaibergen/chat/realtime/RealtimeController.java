package kg.kudaibergen.chat.realtime;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realtime")
@Tag(name = "Realtime")
public class RealtimeController {

   private final CentrifugoTokenService tokens;
   private final CentrifugoClient centrifugo;

   public RealtimeController(CentrifugoTokenService tokens, CentrifugoClient centrifugo) {
      this.tokens = tokens;
      this.centrifugo = centrifugo;
   }

   @GetMapping("/token")
   @Operation(summary = "Токен подключения к Centrifugo",
         description = "Вызывать при подключении клиента и повторно из getToken-коллбэка SDK при "
               + "истечении текущего токена")
   public CentrifugoTokenService.ConnectionToken token(@AuthenticationPrincipal AuthPrincipal principal) {
      return tokens.issue(principal.userId());
   }

   /** Временный эндпоинт для отладки деплоя: publish/presence в ChatService гасят ошибки
    * молча (это правильно для прода), из-за чего "не работает realtime" не видно без логов.
    * Тут — наоборот, реальный результат вызова Server API как есть. Убрать после отладки. */
   @GetMapping("/diagnostics")
   @Operation(summary = "Проверка связи бэкенда с Centrifugo (временный debug-эндпоинт)")
   public String diagnostics() {
      return centrifugo.ping();
   }
}
