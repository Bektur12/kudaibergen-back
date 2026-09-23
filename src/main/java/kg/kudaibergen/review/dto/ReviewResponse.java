package kg.kudaibergen.review.dto;

import java.time.Instant;

import kg.kudaibergen.review.entity.Review;

public record ReviewResponse(Long id, Long storeId, Long authorId, String authorName, short rating,
                             String text, String status, Instant createdAt) {

   public static ReviewResponse of(Review review, String authorName) {
      return new ReviewResponse(review.getId(), review.getStoreId(), review.getAuthorId(), authorName,
            review.getRating(), review.getText(), review.getStatus(), review.getCreatedAt());
   }
}
