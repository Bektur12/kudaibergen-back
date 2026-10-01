package kg.kudaibergen.request;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.request.dto.ReviewDto;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Отзывы")
public class ReviewController {

   private final ReviewService reviews;

   public ReviewController(ReviewService reviews) {
      this.reviews = reviews;
   }

   @GetMapping("/shops/{shopId}/reviews")
   @Operation(summary = "Отзывы магазина (вкладка «Отзывы» на 30)", description = "Новые сверху")
   public CursorPage<ReviewDto> ofShop(@PathVariable Long shopId, @RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer limit,
                                       @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                             required = false) String language) {
      return reviews.ofShop(shopId, cursor, limit, Langs.fromHeader(language));
   }

   @GetMapping("/my/shop/reviews")
   @Operation(summary = "Отзывы моего бокса (21)")
   public CursorPage<ReviewDto> mine(@AuthenticationPrincipal AuthPrincipal principal,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) Integer limit,
                                     @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                           required = false) String language) {
      return reviews.mine(principal.userId(), cursor, limit, Langs.fromHeader(language));
   }

   @PostMapping("/my/shop/reviews/{id}/reply")
   @Operation(summary = "Ответить на отзыв (владелец, один раз)")
   @ApiResponse(responseCode = "409", description = "ALREADY_REPLIED")
   public ReviewDto reply(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                          @Valid @RequestBody ReplyText request,
                          @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                required = false) String language) {
      return reviews.reply(principal.userId(), id, request.text(), Langs.fromHeader(language));
   }

   public record ReplyText(@NotBlank(message = "Напишите ответ") @Size(max = 500, message = "Не длиннее 500 символов")
                           String text) {
   }
}
