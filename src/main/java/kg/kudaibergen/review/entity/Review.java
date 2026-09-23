package kg.kudaibergen.review.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reviews")
public class Review {

   public static final String APPROVED = "APPROVED";
   public static final String PENDING = "PENDING";

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "author_id", nullable = false)
   private Long authorId;

   @Column(name = "store_id", nullable = false)
   private Long storeId;

   @Column(name = "offer_id")
   private Long offerId;

   @Column(nullable = false)
   private short rating;

   @Column(columnDefinition = "text")
   private String text;

   @Column(nullable = false, length = 12)
   private String status = PENDING;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected Review() {
   }

   public Review(Long authorId, Long storeId, Long offerId, short rating, String text, String status) {
      this.authorId = authorId;
      this.storeId = storeId;
      this.offerId = offerId;
      this.rating = rating;
      this.text = text;
      this.status = status;
   }

   public Long getId() {
      return id;
   }

   public Long getAuthorId() {
      return authorId;
   }

   public Long getStoreId() {
      return storeId;
   }

   public Long getOfferId() {
      return offerId;
   }

   public short getRating() {
      return rating;
   }

   public String getText() {
      return text;
   }

   public String getStatus() {
      return status;
   }

   public void setStatus(String status) {
      this.status = status;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
