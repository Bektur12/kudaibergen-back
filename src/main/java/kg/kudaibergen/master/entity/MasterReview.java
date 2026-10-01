package kg.kudaibergen.master.entity;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kg.kudaibergen.request.entity.ReviewTag;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Оценка мастеру при закрытии заявки «Договорились» (37). */
@Entity
@Table(name = "master_reviews")
public class MasterReview {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "master_id", nullable = false, updatable = false)
   private Long masterId;

   @Column(name = "buyer_id", updatable = false)
   private Long buyerId;

   @Column(name = "request_id", updatable = false)
   private Long requestId;

   @Column(nullable = false)
   private short stars;

   @JdbcTypeCode(SqlTypes.ARRAY)
   @Column(nullable = false, columnDefinition = "text[]")
   private List<String> tags;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   /** Скрыто администрацией (модерация). Пишет только админка, JPA поле не меняет. */
   @Column(name = "hidden_by_admin", insertable = false, updatable = false)
   private boolean hiddenByAdmin;

   @Column(name = "hidden_reason", insertable = false, updatable = false)
   private String hiddenReason;

   protected MasterReview() {
   }

   public MasterReview(Long masterId, Long buyerId, Long requestId, int stars, List<ReviewTag> tags, Instant now) {
      this.masterId = masterId;
      this.buyerId = buyerId;
      this.requestId = requestId;
      this.stars = (short) stars;
      this.tags = tags.stream().map(Enum::name).toList();
      this.createdAt = now;
   }

   public Long getId() {
      return id;
   }

   public Long getMasterId() {
      return masterId;
   }

   public Long getBuyerId() {
      return buyerId;
   }

   public short getStars() {
      return stars;
   }

   public List<ReviewTag> getTags() {
      return tags.stream().map(ReviewTag::valueOf).toList();
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
