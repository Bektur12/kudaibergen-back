package kg.kudaibergen.request.entity;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Оценка бокса при закрытии запроса (экран 09). Ответ продавца на отзыв — экран 21. */
@Entity
@Table(name = "reviews")
public class Review {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "shop_id", nullable = false, updatable = false)
   private Long shopId;

   @Column(name = "buyer_id", updatable = false)
   private Long buyerId;

   @Column(name = "request_id", updatable = false)
   private Long requestId;

   @Column(nullable = false)
   private short stars;

   @JdbcTypeCode(SqlTypes.ARRAY)
   @Column(nullable = false, columnDefinition = "text[]")
   private List<String> tags;

   @Column(name = "reply_text", length = 500)
   private String replyText;

   @Column(name = "replied_at")
   private Instant repliedAt;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   /** Скрыто администрацией (модерация). Пишет только админка, JPA поле не меняет. */
   @Column(name = "hidden_by_admin", insertable = false, updatable = false)
   private boolean hiddenByAdmin;

   @Column(name = "hidden_reason", insertable = false, updatable = false)
   private String hiddenReason;

   protected Review() {
   }

   public Review(Long shopId, Long buyerId, Long requestId, int stars, List<ReviewTag> tags, Instant now) {
      this.shopId = shopId;
      this.buyerId = buyerId;
      this.requestId = requestId;
      this.stars = (short) stars;
      this.tags = tags.stream().map(Enum::name).toList();
      this.createdAt = now;
   }

   /** Один ответ продавца на отзыв (экран 21). */
   public void reply(String text, Instant now) {
      replyText = text;
      repliedAt = now;
   }

   public Long getId() {
      return id;
   }

   public Long getShopId() {
      return shopId;
   }

   public Long getBuyerId() {
      return buyerId;
   }

   public Long getRequestId() {
      return requestId;
   }

   public short getStars() {
      return stars;
   }

   public List<ReviewTag> getTags() {
      return tags.stream().map(ReviewTag::valueOf).toList();
   }

   public String getReplyText() {
      return replyText;
   }

   public Instant getRepliedAt() {
      return repliedAt;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public boolean isHiddenByAdmin() {
      return hiddenByAdmin;
   }

   public String getHiddenReason() {
      return hiddenReason;
   }
}
