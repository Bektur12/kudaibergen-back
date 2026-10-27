package kg.kudaibergen.catalog.comment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parts/{partId}/comments")
@Tag(name = "Каталог")
public class PartCommentController {

   private final PartCommentService comments;

   public PartCommentController(PartCommentService comments) {
      this.comments = comments;
   }

   @GetMapping
   @Operation(summary = "Комментарии под видео товара", description = "Новые сверху, по 20. Без входа — тоже.")
   public PartCommentDto.PartCommentPageDto list(@AuthenticationPrincipal AuthPrincipal principal,
                                                 @PathVariable Long partId,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(required = false) Integer limit) {
      return comments.page(principal == null ? null : principal.userId(), partId, cursor, limit);
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Написать комментарий", description = "До 500 символов. Продавцу бокса — пуш.")
   @ApiResponse(responseCode = "429", description = "COMMENT_LIMIT — больше 30 в час")
   public PartCommentDto add(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long partId,
                             @Valid @RequestBody PartCommentInput input) {
      return comments.add(principal.userId(), partId, input);
   }

   @DeleteMapping("/{commentId}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить комментарий", description = "Свой — автор, любой под своим товаром — продавец бокса.")
   public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long partId,
                      @PathVariable Long commentId) {
      comments.delete(principal.userId(), partId, commentId);
   }
}
