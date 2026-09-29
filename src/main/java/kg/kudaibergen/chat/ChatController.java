package kg.kudaibergen.chat;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.chat.dto.ChatDto;
import kg.kudaibergen.chat.dto.ChatInputs;
import kg.kudaibergen.chat.dto.ChatListItemDto;
import kg.kudaibergen.chat.dto.MessageDto;
import kg.kudaibergen.chat.dto.QuickReplyDto;
import kg.kudaibergen.chat.dto.UnreadDto;
import kg.kudaibergen.chat.entity.ChatSide;
import kg.kudaibergen.chat.entity.MessageType;
import kg.kudaibergen.chat.realtime.CentrifugoTokens;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.complaint.ComplaintDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Чаты покупателя с магазином (экраны 08, 13, 16). Живая доставка — Centrifugo, см. /realtime/token. */
@RestController
@RequestMapping("/api/v1/chats")
@Tag(name = "Чаты")
public class ChatController {

   private final ChatService chats;

   public ChatController(ChatService chats) {
      this.chats = chats;
   }

   @PostMapping
   @Operation(summary = "Открыть чат с магазином («Написать» на 07, 29, 30)", description = """
         С requestId — чат по запросу (создаётся ответом «Есть»); без — прямой чат из профиля магазина.
         Повторный вызов возвращает тот же чат.""")
   @ApiResponse(responseCode = "400", description = "SELF_CHAT, SHOP_NOT_REPLIED")
   public ChatDto open(@AuthenticationPrincipal AuthPrincipal principal,
                       @Valid @RequestBody ChatInputs.OpenChat request,
                       @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                             required = false) String language) {
      return chats.open(principal.userId(), request, Langs.fromHeader(language));
   }

   @GetMapping
   @Operation(summary = "Список чатов (16)", description = """
         as=BUYER — мои чаты как покупателя, as=SHOP — чаты моего бокса (общие для владельца и сотрудников).
         Свежие сверху; closed-запросы — requestClosed=true (рисовать серым).""")
   public CursorPage<ChatListItemDto> list(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestParam(defaultValue = "BUYER") ChatSide as,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer limit,
                                           @Parameter(hidden = true) @RequestHeader(
                                                 value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return chats.list(principal.userId(), as, cursor, limit, Langs.fromHeader(language));
   }

   @GetMapping("/unread")
   @Operation(summary = "Бейдж вкладки «Чаты»", description = "Непрочитанные как покупатель и как продавец бокса")
   public UnreadDto unread(@AuthenticationPrincipal AuthPrincipal principal) {
      return chats.unread(principal.userId());
   }

   @GetMapping("/{id}")
   @Operation(summary = "Шапка чата (08, 13)", description = "Магазин или покупатель, закреплённый запрос, «в сети», канал")
   public ChatDto get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                      @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                            required = false) String language) {
      return chats.get(principal.userId(), id, Langs.fromHeader(language));
   }

   @GetMapping("/{id}/subscription-token")
   @Operation(summary = "Токен подписки на канал чата в Centrifugo",
         description = "Только участнику. Вызывать при подписке и из getToken-коллбэка подписки в SDK")
   public CentrifugoTokens.Token subscriptionToken(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @PathVariable Long id) {
      return chats.subscriptionToken(principal.userId(), id);
   }

   @GetMapping("/{id}/messages")
   @Operation(summary = "История (новые первыми)", description = "cursor — из nextCursor, листает в прошлое")
   public CursorPage<MessageDto> messages(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                          @RequestParam(required = false) String cursor,
                                          @RequestParam(required = false) Integer limit) {
      return chats.messages(principal.userId(), id, cursor, limit);
   }

   @PostMapping("/{id}/messages")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Текст или быстрый ответ", description = """
         {text} или {quickReply}: покупатель — ARRIVED («Я на месте» с экрана 18), продавец — RESERVED, ROUTE, SOLD.
         clientId — из очереди отправки без сети: повтор вернёт то же сообщение.""")
   @ApiResponse(responseCode = "403", description = "CHAT_BLOCKED, SHOP_NOT_ACTIVE")
   public MessageDto send(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                          @Valid @RequestBody ChatInputs.SendMessage request) {
      return chats.send(principal.userId(), id, request);
   }

   @PostMapping(value = "/{id}/messages/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Фото, голосовое или видео", description = "Фото до 10 МБ, голосовое до 60 секунд")
   public MessageDto sendMedia(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                               @RequestParam MultipartFile file,
                               @RequestParam MessageType type,
                               @RequestParam(required = false) String caption,
                               @Parameter(description = "Для VOICE и VIDEO, секунды")
                               @RequestParam(required = false) Integer durationSeconds,
                               @Parameter(description = "Для VOICE: JSON-массив до 100 пиков громкости 0..1")
                               @RequestParam(required = false) String waveform,
                               @RequestParam(required = false) String clientId) {
      return chats.sendMedia(principal.userId(), id, file, type, caption, durationSeconds, waveform, clientId);
   }

   @PostMapping("/{id}/read")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Прочитано", description = "До upToMessageId (по умолчанию — до последнего) за всю свою сторону")
   public void read(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                    @RequestBody(required = false) ChatInputs.Read request) {
      chats.read(principal.userId(), id, request == null ? null : request.upToMessageId());
   }

   @GetMapping("/{id}/quick-replies")
   @Operation(summary = "Быстрые ответы и шаблоны над полем ввода",
         description = "MESSAGE — отправить {quickReply: code}; ACTION — действие клиента; templateId — вставить text")
   public List<QuickReplyDto> quickReplies(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                           @Parameter(hidden = true) @RequestHeader(
                                                 value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return chats.quickReplies(principal.userId(), id, Langs.fromHeader(language));
   }

   @PutMapping("/{id}/block")
   @Operation(summary = "Заблокировать собеседника (меню чата)")
   public ChatDto block(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                        @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                              required = false) String language) {
      return chats.block(principal.userId(), id, true, Langs.fromHeader(language));
   }

   @DeleteMapping("/{id}/block")
   @Operation(summary = "Разблокировать")
   public ChatDto unblock(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                          @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                required = false) String language) {
      return chats.block(principal.userId(), id, false, Langs.fromHeader(language));
   }

   @PostMapping("/{id}/complaints")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Пожаловаться (меню чата)", description = "Жалоба уходит админу рынка на модерацию")
   public ComplaintDto complain(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                @Valid @RequestBody(required = false) ChatInputs.ChatComplaint request) {
      return chats.complain(principal.userId(), id, request == null ? null : request.text());
   }
}
