package kg.kudaibergen.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.catalog.dto.MyPartItemDto;
import kg.kudaibergen.catalog.dto.MyPartsSummaryDto;
import kg.kudaibergen.catalog.dto.PartDetailDto;
import kg.kudaibergen.catalog.dto.PartInput;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.idempotency.Idempotent;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Каталог продавца (вкладка «Товары», экраны 24–26): владелец и сотрудники бокса. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Мои запчасти")
public class MyPartsController {

   private final MyPartsService parts;

   public MyPartsController(MyPartsService parts) {
      this.parts = parts;
   }

   @GetMapping("/my/parts")
   @Operation(summary = "Мои запчасти (24)", description = "filter: ALL (без архива), IN_STOCK, OUT_OF_STOCK, DRAFT, ARCHIVED; q — название или номер")
   public CursorPage<MyPartItemDto> list(@AuthenticationPrincipal AuthPrincipal principal,
                                         @RequestParam(defaultValue = "ALL") MyPartsService.Filter filter,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(required = false) Integer limit,
                                         @Parameter(hidden = true) @RequestHeader(
                                               value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      return parts.list(principal.userId(), filter, q, cursor, limit, Langs.fromHeader(language));
   }

   @GetMapping("/my/parts/summary")
   @Operation(summary = "Счётчики чипов и «посмотрели N раз за неделю» (24)")
   public MyPartsSummaryDto summary(@AuthenticationPrincipal AuthPrincipal principal) {
      return parts.summary(principal.userId());
   }

   @GetMapping("/my/parts/{id}")
   @Operation(summary = "Запчасть для редактирования (26), в том числе черновик")
   public PartDetailDto get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                  required = false) String language) {
      return parts.get(principal.userId(), id, Langs.fromHeader(language));
   }

   @PostMapping("/parts")
   @ResponseStatus(HttpStatus.CREATED)
   @Idempotent
   @Operation(summary = "Новая запчасть (26)", description = """
         Сохраняется черновиком (поля можно заполнять постепенно через PATCH). publish=true — сразу опубликовать:
         нужны название (3–120), категория, состояние, цена, 1–6 фото (mediaIds из POST /media/photos) и 1–20 машин.""")
   @ApiResponse(responseCode = "400", description = "PART_INCOMPLETE (missing — какие поля), BAD_PHOTO, MODEL_MISMATCH")
   @ApiResponse(responseCode = "409", description = "ACTIVE_PARTS_LIMIT — уже 500 запчастей в продаже")
   public PartDetailDto create(@AuthenticationPrincipal AuthPrincipal principal,
                               @Valid @RequestBody PartInput request,
                               @RequestParam(defaultValue = "false") boolean publish,
                               @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                     required = false) String language) {
      return parts.create(principal.userId(), request, publish, Langs.fromHeader(language));
   }

   @PatchMapping("/parts/{id}")
   @Operation(summary = "Изменить запчасть (26, свайп «Нет в наличии» — quantity: 0)",
         description = "null — не менять; mediaIds и fitments заменяются целиком. Опубликованная должна остаться полной")
   public PartDetailDto update(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                               @Valid @RequestBody PartInput request,
                               @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                     required = false) String language) {
      return parts.update(principal.userId(), id, request, Langs.fromHeader(language));
   }

   @PostMapping("/parts/{id}/publish")
   @Operation(summary = "Опубликовать черновик («Опубликовать» на 26)")
   public PartDetailDto publish(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                      required = false) String language) {
      return parts.publish(principal.userId(), id, Langs.fromHeader(language));
   }

   @PostMapping("/parts/{id}/archive")
   @Operation(summary = "В архив (свайп на 24)")
   public PartDetailDto archive(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                      required = false) String language) {
      return parts.archive(principal.userId(), id, Langs.fromHeader(language));
   }

   @PostMapping("/parts/{id}/restore")
   @Operation(summary = "Вернуть из архива", description = "Полная — снова в продаже, неполная — в черновики")
   public PartDetailDto restore(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                      required = false) String language) {
      return parts.restore(principal.userId(), id, Langs.fromHeader(language));
   }

   @DeleteMapping("/parts/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить (только владелец бокса)")
   @ApiResponse(responseCode = "403", description = "OWNER_ONLY")
   public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      parts.delete(principal.userId(), id);
   }
}
