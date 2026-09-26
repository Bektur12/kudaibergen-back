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

   public RealtimeController(CentrifugoTokenService tokens) {
      this.tokens = tokens;
   }

   @GetMapping("/token")
   @Operation(summary = "Токен подключения к Centrifugo",
         description = "Вызывать при подключении клиента и повторно из getToken-коллбэка SDK при "
               + "истечении текущего токена")
   public CentrifugoTokenService.ConnectionToken token(@AuthenticationPrincipal AuthPrincipal principal) {
      return tokens.issue(principal.userId());
   }
}
