package kg.kudaibergen.request.entity;

import java.time.Duration;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Запрос «Найти запчасть» (экраны 05, 06, 06б, 07, 09, 20, 32). Машина хранится снимком: её могут удалить
 * из гаража. Живёт до expiresAt, потом EXPIRED; покупатель продлевает до {@link #MAX_EXTENSIONS} раз.
 */
@Entity
@Table(name = "part_requests")
public class PartRequest {

   public static final int MAX_EXTENSIONS = 3;

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

   /** Подсказка «+ Колодки», с которой начали запрос: из них считаются подсказки для этой машины. */
   @Column(name = "hint_id", updatable = false)
   private Long hintId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 10)
   private RequestTarget target;

   @JdbcTypeCode(SqlTypes.ARRAY)
   @Column(name = "target_row_ids", nullable = false, columnDefinition = "bigint[]")
   private List<Long> targetRowIds = new ArrayList<>();

   @JdbcTypeCode(SqlTypes.ARRAY)
   @Column(name = "target_container_ids", nullable = false, columnDefinition = "bigint[]")
   private List<Long> targetContainerIds = new ArrayList<>();

   /** id фото (media) по порядку, до 3. */
   @ElementCollection
   @CollectionTable(name = "request_photos", joinColumns = @JoinColumn(name = "request_id"))
   @OrderColumn(name = "sort")
   @Column(name = "media_id", nullable = false)
   private List<Long> photoIds = new ArrayList<>();

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 7)
   private RequestStatus status = RequestStatus.ACTIVE;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 10)
   private RequestDuration duration;

   @Column(name = "expires_at", nullable = false)
   private Instant expiresAt;

   @Column(name = "extended_times", nullable = false)
   private short extendedTimes;

   @Column(name = "closed_with_shop_id")
   private Long closedWithShopId;

   @Column(name = "closed_at")
   private Instant closedAt;

   @Column(name = "recipients_count", nullable = false)
   private int recipientsCount;

   @Column(name = "have_count", nullable = false)
   private int haveCount;

   /** Начало текущего окна ожидания: создание, расширение адресатов, продление после истечения. */
   @Column(name = "sent_at", nullable = false)
   private Instant sentAt;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   protected PartRequest() {
   }

   public PartRequest(Long buyerId, Long carId, Long brandId, Long modelId, short year, String text, Long categoryId,
                      Long hintId, RequestTarget target, List<Long> targetRowIds, List<Long> targetContainerIds,
                      List<Long> photoIds, RequestDuration duration, Instant now) {
      this.buyerId = buyerId;
      this.carId = carId;
      this.brandId = brandId;
      this.modelId = modelId;
      this.year = year;
      this.text = text;
      this.categoryId = categoryId;
      this.hintId = hintId;
      this.target = target;
      this.targetRowIds = new ArrayList<>(targetRowIds);
      this.targetContainerIds = new ArrayList<>(targetContainerIds);
      this.photoIds = new ArrayList<>(photoIds);
      this.duration = duration;
      this.sentAt = now;
      this.expiresAt = duration.expiresAt(now);
      this.createdAt = now;
   }

   /** Продавцы видят запрос и могут ответить. */
   public boolean isActive() {
      return status == RequestStatus.ACTIVE;
   }

   /** Не закрыт покупателем: активный или истёкший — его ещё можно закрыть «Купил», продлить, расширить. */
   public boolean isOpen() {
      return status != RequestStatus.CLOSED;
   }

   public boolean canExtend() {
      return isOpen() && extendedTimes < MAX_EXTENSIONS;
   }

   /** Рассылка ушла ещё {@code added} боксам. */
   public void dispatched(int added) {
      recipientsCount += added;
   }

   /** «Отправить всему рынку» (20, 32): срок отсчитывается заново, истёкший запрос снова активен. */
   public void widenToMarket(Instant now) {
      target = RequestTarget.MARKET;
      targetRowIds = new ArrayList<>();
      targetContainerIds = new ArrayList<>();
      restart(now, duration.expiresAt(now));
   }

   /**
    * «Продлить» (32): активный — срок сдвигается на {@code by}; истёкший — снова активен на {@code by} от сейчас.
    * Счётчик продлений проверяет вызывающий ({@link #canExtend()}).
    */
   public void extend(Duration by, Instant now) {
      extendedTimes++;
      if (isActive()) {
         expiresAt = expiresAt.plus(by);
      } else {
         restart(now, now.plus(by));
      }
   }

   private void restart(Instant now, Instant until) {
      status = RequestStatus.ACTIVE;
      closedAt = null;
      sentAt = now;
      expiresAt = until;
   }

   public void haveCountChanged(int delta) {
      haveCount = Math.max(0, haveCount + delta);
   }

   public void close(Long shopId, Instant now) {
      status = RequestStatus.CLOSED;
      closedWithShopId = shopId;
      closedAt = now;
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

   public Long getHintId() {
      return hintId;
   }

   public RequestTarget getTarget() {
      return target;
   }

   public List<Long> getTargetRowIds() {
      return List.copyOf(targetRowIds);
   }

   public List<Long> getTargetContainerIds() {
      return List.copyOf(targetContainerIds);
   }

   public List<Long> getPhotoIds() {
      return List.copyOf(photoIds);
   }

   public RequestStatus getStatus() {
      return status;
   }

   public RequestDuration getDuration() {
      return duration;
   }

   public Instant getExpiresAt() {
      return expiresAt;
   }

   public int getExtendedTimes() {
      return extendedTimes;
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

   public Instant getCreatedAt() {
      return createdAt;
   }
}
