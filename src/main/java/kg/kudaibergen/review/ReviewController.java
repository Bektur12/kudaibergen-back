package kg.kudaibergen.review;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.PageResponse;
import kg.kudaibergen.review.dto.CreateReviewRequest;
import kg.kudaibergen.review.dto.ReviewResponse;
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

@RestController
@RequestMapping("/api/v1/stores/{storeId}/reviews")
@Validated
@Tag(name = "Отзывы")
public class ReviewController {

   private final ReviewService reviewService;

   public ReviewController(ReviewService reviewService) {
      this.reviewService = reviewService;
   }

   @GetMapping
   @Operation(summary = "Отзывы магазина")
   public PageResponse<ReviewResponse> list(@PathVariable Long storeId,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
      return reviewService.byStore(storeId, page, size);
   }

   @PostMapping
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Оставить отзыв", description = "Доступно после принятой сделки с магазином")
   public ReviewResponse create(@AuthenticationPrincipal AuthPrincipal principal,
                                @PathVariable Long storeId,
                                @Valid @RequestBody CreateReviewRequest request) {
      return reviewService.create(principal.userId(), storeId, request);
   }
}
