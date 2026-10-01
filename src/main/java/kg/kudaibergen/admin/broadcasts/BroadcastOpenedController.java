package kg.kudaibergen.admin.broadcasts;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Приложение: пользователь открыл пуш рассылки (data.type = BROADCAST) — для «% открыли» в админке. */
@RestController
@RequestMapping("/api/v1/broadcasts")
@Tag(name = "Уведомления")
public class BroadcastOpenedController {

   private final BroadcastService broadcasts;

   public BroadcastOpenedController(BroadcastService broadcasts) {
      this.broadcasts = broadcasts;
   }

   @PostMapping("/{id}/opened")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Открыл пуш рассылки", description = "id — data.broadcastId из пуша. Повтор ничего не меняет")
   public void opened(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal) {
      broadcasts.opened(id, principal.userId());
   }
}
