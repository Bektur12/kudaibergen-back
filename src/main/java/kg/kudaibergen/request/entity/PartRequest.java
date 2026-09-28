package kg.kudaibergen.request.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Запрос «Найти запчасть» (экраны 05, 06, 07, 09, 20). Машина хранится снимком: её могут удалить из гаража. */
@Entity
@Table(name = "part_requests")
public class PartRequest {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "buyer_id", nullable = false, updatable = false)
   private Long buyerId;

   @Column(name = "car_id")
   private Long carId;

   @Column(name = "brand_id", nullable = false, updatable = false)
   private Long brandId;

   @Column(name = "model_id", nullable = false, updatable = false)
   private Long modelId;

   @Column(nullable = false, updatable = false)
   private short year;

   @Column(nullable = false, length = 200, updatable = false)
   private String text;

   @Column(name = "category_id", updatable = false)
   private Long categoryId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 6)
   private RequestTarget target;

   @Column(name = "target_row_id")
   private Long targetRowId;

   @Column(name = "target_shop_id")
   private Long targetShopId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 7)
   private RequestStatus status = RequestStatus.OPEN;

   @Column(name = "closed_with_shop_id")
   private Long closedWithShopId;

   @Column(name = "closed_at")
   private Instant closedAt;

   @Column(name = "recipients_count", nullable = false)
   private int recipientsCount;

   @Column(name = "have_count", nullable = false)
   private int haveCount;

   @Column(name = "sent_at", nullable = false)
   private Instant sentAt;

   @Column(name = "no_reply_at")
   private Instant noReplyAt;

   @Column(name = "last_activity_at", nullable = false)
   private Instant lastActivityAt;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   protected PartRequest() {
   }

   public PartRequest(Long buyerId, Long carId, Long brandId, Long modelId, short year, String text, Long categoryId,
                      RequestTarget target, Long targetRowId, Long targetShopId, Instant now) {
      this.buyerId = buyerId;
      this.carId = carId;
      this.brandId = brandId;
      this.modelId = modelId;
      this.year = year;
      this.text = text;
      this.categoryId = categoryId;
      this.target = target;
      this.targetRowId = targetRowId;
      this.targetShopId = targetShopId;
      this.sentAt = now;
      this.lastActivityAt = now;
      this.createdAt = now;
   }

   public boolean isOpen() {
      return status == RequestStatus.OPEN;
   }

   /** Рассылка ушла ещё {@code added} боксам. */
   public void dispatched(int added, Instant now) {
      recipientsCount += added;
      lastActivityAt = now;
   }

   /** «Отправить всему рынку» (экран 20): таймер «никто не ответил» начинается заново. */
   public void widenToMarket(Instant now) {
      target = RequestTarget.MARKET;
      targetRowId = null;
      targetShopId = null;
      sentAt = now;
      noReplyAt = null;
      lastActivityAt = now;
   }

   public void haveCountChanged(int delta, Instant now) {
      haveCount = Math.max(0, haveCount + delta);
      lastActivityAt = now;
   }

   public void touch(Instant now) {
      lastActivityAt = now;
   }

   public void markNoReply(Instant now) {
      noReplyAt = now;
   }

   public void close(Long shopId, Instant now) {
      status = RequestStatus.CLOSED;
      closedWithShopId = shopId;
      closedAt = now;
      lastActivityAt = now;
   }

   public void expire(Instant now) {
      status = RequestStatus.EXPIRED;
      closedAt = now;
   }

   public Long getId() {
      return id;
   }

   public Long getBuyerId() {
      return buyerId;
   }

   public Long getCarId() {
      return carId;
   }

   public Long getBrandId() {
      return brandId;
   }

   public Long getModelId() {
      return modelId;
   }

   public short getYear() {
      return year;
   }

   public String getText() {
      return text;
   }

   public Long getCategoryId() {
      return categoryId;
   }

   public RequestTarget getTarget() {
      return target;
   }

   public Long getTargetRowId() {
      return targetRowId;
   }

   public Long getTargetShopId() {
      return targetShopId;
   }

   public RequestStatus getStatus() {
      return status;
   }

   public Long getClosedWithShopId() {
      return closedWithShopId;
   }

   public Instant getClosedAt() {
      return closedAt;
   }

   public int getRecipientsCount() {
      return recipientsCount;
   }

   public int getHaveCount() {
      return haveCount;
   }

   public Instant getSentAt() {
      return sentAt;
   }

   public Instant getNoReplyAt() {
      return noReplyAt;
   }

   public Instant getLastActivityAt() {
      return lastActivityAt;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
