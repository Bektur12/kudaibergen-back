package kg.kudaibergen.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.chat.dto.ChatResponse;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Написать магазину напрямую с карточки магазина — без запроса и предложения. */
@RestController
@RequestMapping("/api/v1/stores")
@Tag(name = "Чаты")
public class StoreChatController {

   private final ChatService chatService;

   public StoreChatController(ChatService chatService) {
      this.chatService = chatService;
   }

   @PostMapping("/{id}/chat")
   @ResponseStatus(HttpStatus.OK)
   @Operation(summary = "Открыть чат с магазином",
         description = "Идемпотентно: если чат с этим магазином уже есть — вернётся он же")
   public ChatResponse startChat(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return chatService.startChat(principal.userId(), id);
   }
}
