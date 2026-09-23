package kg.kudaibergen.chat;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import kg.kudaibergen.chat.dto.ChatResponse;
import kg.kudaibergen.chat.dto.MessageResponse;
import kg.kudaibergen.chat.dto.SendMessageRequest;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/chats")
@Validated
@Tag(name = "Чаты")
public class ChatController {

   private final ChatService chatService;

   public ChatController(ChatService chatService) {
      this.chatService = chatService;
   }

   @GetMapping
   @Operation(summary = "Мои чаты")
   public List<ChatResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
      return chatService.list(principal.userId());
   }

   @GetMapping("/{id}/messages")
   @Operation(summary = "Сообщения чата (новые сверху)")
   public PageResponse<MessageResponse> messages(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable Long id,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
      return chatService.messages(id, principal.userId(), page, size);
   }

   @PostMapping("/{id}/messages")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Отправить сообщение")
   public MessageResponse send(@AuthenticationPrincipal AuthPrincipal principal,
                               @PathVariable Long id,
                               @Valid @RequestBody SendMessageRequest request) {
      return chatService.send(id, principal.userId(), request);
   }

   @PostMapping(value = "/{id}/messages/media", consumes = "multipart/form-data")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Отправить фото/голосовое/видео")
   public MessageResponse sendMedia(@AuthenticationPrincipal AuthPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestParam MultipartFile file,
                                    @RequestParam @Pattern(regexp = "PHOTO|VOICE|VIDEO",
                                          message = "Тип: PHOTO, VOICE или VIDEO") String type,
                                    @RequestParam(required = false) String caption,
                                    @Parameter(description = "Для VOICE/VIDEO, в секундах")
                                    @RequestParam(required = false) Integer durationSeconds,
                                    @Parameter(description = "Только для VOICE: JSON-массив пиков громкости 0..1, до 100 значений")
                                    @RequestParam(required = false) String waveform) {
      return chatService.sendMedia(id, principal.userId(), file, type, caption, durationSeconds, waveform);
   }

   @PostMapping("/{id}/read")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Отметить сообщения прочитанными")
   public void read(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      chatService.markRead(id, principal.userId());
   }
}
