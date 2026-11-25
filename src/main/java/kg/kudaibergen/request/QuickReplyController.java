package kg.kudaibergen.request;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.request.dto.RequestInputs;
import kg.kudaibergen.request.entity.ReplyAnswer;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * «Нет» с экрана блокировки (Live Activity на iPhone): без токена сессии, по ключу ответа из карточки
 * ({@link QuickReplyTokens}). Только «Нет»: «Есть» открывает приложение — там цена и состояние.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Входящие запросы")
public class QuickReplyController {

   private final IncomingRequestService incoming;
   private final QuickReplyTokens tokens;

   public QuickReplyController(IncomingRequestService incoming, QuickReplyTokens tokens) {
      this.incoming = incoming;
      this.tokens = tokens;
   }

   public record LockScreenReply(@NotBlank @Size(max = 300) String token) {
   }

   @PostMapping("/requests/{id}/quick-reply")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "«Нет» с экрана блокировки (Live Activity)", description = """
         Без входа: token — ключ ответа из карточки Live Activity, годен для этого запроса и магазина до конца запроса.""")
   @ApiResponse(responseCode = "403", description = "QUICK_REPLY_TOKEN — ключ чужой, подделан или истёк")
   @ApiResponse(responseCode = "409", description = "REQUEST_CLOSED, REQUEST_EXPIRED, ALREADY_REPLIED")
   public void quickReply(@PathVariable Long id, @Valid @RequestBody LockScreenReply request) {
      Long userId = tokens.verify(request.token(), id);
      if (userId == null) {
         throw new ForbiddenException("QUICK_REPLY_TOKEN", "Ответьте в приложении");
      }
      incoming.reply(userId, id, new RequestInputs.Reply(ReplyAnswer.NOT_HAVE, null, null, null, null, null, null,
            null, null, null, null));
   }
}
