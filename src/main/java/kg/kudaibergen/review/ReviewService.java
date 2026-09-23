package kg.kudaibergen.review;

import java.math.BigDecimal;
import java.math.RoundingMode;

import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.error.ForbiddenException;
import kg.kudaibergen.common.web.PageResponse;
import kg.kudaibergen.review.dto.CreateReviewRequest;
import kg.kudaibergen.review.dto.ReviewResponse;
import kg.kudaibergen.review.entity.Review;
import kg.kudaibergen.store.DealAccess;
import kg.kudaibergen.store.StoreService;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

   private final ReviewRepository reviews;
   private final StoreService storeService;
   private final DealAccess dealAccess;
   private final UserRepository users;

   public ReviewService(ReviewRepository reviews, StoreService storeService, DealAccess dealAccess,
                        UserRepository users) {
      this.reviews = reviews;
      this.storeService = storeService;
      this.dealAccess = dealAccess;
      this.users = users;
   }

   /** Отзыв оставляет только тот, у кого с магазином была принятая сделка. */
   @Transactional
   public ReviewResponse create(Long authorId, Long storeId, CreateReviewRequest request) {
      storeService.getRequired(storeId);
      if (!dealAccess.hasAcceptedDeal(authorId, storeId)) {
         throw new ForbiddenException("REVIEW_NOT_ALLOWED",
               "Отзыв можно оставить только после принятой сделки с этим магазином");
      }
      if (reviews.existsByAuthorIdAndStoreId(authorId, storeId)) {
         throw new ConflictException("REVIEW_ALREADY_EXISTS", "Вы уже оставляли отзыв этому магазину");
      }

      Review review = reviews.save(new Review(authorId, storeId, request.offerId(), request.rating(),
            request.text(), Review.APPROVED));
      recalculateRating(storeId);

      String authorName = users.findById(authorId).map(User::getName).orElse(null);
      return ReviewResponse.of(review, authorName);
   }

   @Transactional(readOnly = true)
   public PageResponse<ReviewResponse> byStore(Long storeId, int page, int size) {
      var found = reviews.findByStoreIdAndStatusOrderByCreatedAtDesc(storeId, Review.APPROVED,
            PageRequest.of(page, size));
      return PageResponse.of(found, review -> ReviewResponse.of(review,
            users.findById(review.getAuthorId()).map(User::getName).orElse(null)));
   }

   /** Рейтинг магазина — среднее по одобренным отзывам, округлённое до десятых. */
   private void recalculateRating(Long storeId) {
      ReviewRepository.RatingAggregate aggregate = reviews.aggregate(storeId);
      double average = aggregate.getAverage() == null ? 0 : aggregate.getAverage();
      int count = aggregate.getTotal() == null ? 0 : aggregate.getTotal().intValue();
      storeService.applyRating(storeId, BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP), count);
   }
}
