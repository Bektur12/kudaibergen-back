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
@Tag(name = "Чаты")
public class RealtimeController {

   private final CentrifugoTokens tokens;

   public RealtimeController(CentrifugoTokens tokens) {
      this.tokens = tokens;
   }

   @GetMapping("/token")
   @Operation(summary = "Токен подключения к Centrifugo", description = """
         При подключении и из getToken-коллбэка SDK. После подключения клиент подписывается на свой
         inbox:{userId}#{userId} (без токена) и на chat:{id} открытого чата (токен — /chats/{id}/subscription-token).""")
   public CentrifugoTokens.Token token(@AuthenticationPrincipal AuthPrincipal principal) {
      return tokens.connection(principal.userId());
   }
}
