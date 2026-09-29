package kg.kudaibergen.request.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** Ответ бокса на запрос (экраны 11, 12, 14). Один на бокс: ответ сотрудника засчитывается за магазин. */
@Entity
@Table(name = "request_replies")
public class RequestReply {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "request_id", nullable = false, updatable = false)
   private Long requestId;

   @Column(name = "shop_id", nullable = false, updatable = false)
   private Long shopId;

   @Column(name = "author_id", updatable = false)
   private Long authorId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8)
   private ReplyAnswer answer;

   @Enumerated(EnumType.STRING)
   @Column(length = 9)
   private PartCondition condition;

   @Column(length = 300)
   private String message;

   private Integer price;

   @Column(name = "part_id")
   private Long partId;

   /** Фото к ответу «Есть» по порядку, до 3. */
   @ElementCollection
   @CollectionTable(name = "reply_photos", joinColumns = @JoinColumn(name = "reply_id"))
   @OrderColumn(name = "sort")
   @Column(name = "media_id", nullable = false)
   private List<Long> photoIds = new ArrayList<>();

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt;

   protected RequestReply() {
   }

   public RequestReply(Long requestId, Long shopId, Long authorId, Instant now) {
      this.requestId = requestId;
      this.shopId = shopId;
      this.authorId = authorId;
      this.createdAt = now;
      this.updatedAt = now;
   }

   /** «Нет» — детали ответа не нужны. */
   public void fill(ReplyAnswer answer, PartCondition condition, String message, Integer price, Long partId,
                    List<Long> photoIds, Instant now) {
      this.answer = answer;
      boolean have = answer == ReplyAnswer.HAVE;
      this.condition = have ? condition : null;
      this.message = have ? message : null;
      this.price = have ? price : null;
      this.partId = have ? partId : null;
      this.photoIds.clear();
      if (have) {
         this.photoIds.addAll(photoIds);
      }
      this.updatedAt = now;
   }

   public boolean isHave() {
      return answer == ReplyAnswer.HAVE;
   }

   public Long getId() {
      return id;
   }

   public Long getRequestId() {
      return requestId;
   }

   public Long getShopId() {
      return shopId;
   }

   public Long getAuthorId() {
      return authorId;
   }

   public ReplyAnswer getAnswer() {
      return answer;
   }

   public PartCondition getCondition() {
      return condition;
   }

   public String getMessage() {
      return message;
   }

   public Integer getPrice() {
      return price;
   }

   public Long getPartId() {
      return partId;
   }

   public List<Long> getPhotoIds() {
      return List.copyOf(photoIds);
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getUpdatedAt() {
      return updatedAt;
   }
}
