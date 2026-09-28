package kg.kudaibergen.shop.entity;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Магазин продавца в контейнере (экраны 10а, 10, 21, 22, 23, 30). */
@Entity
@Table(name = "shops")
public class Shop {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "owner_id", nullable = false)
   private Long ownerId;

   @Column(nullable = false, length = 60)
   private String name;

   @Column(name = "container_id", nullable = false)
   private Long containerId;

   @Column(name = "pending_container_id")
   private Long pendingContainerId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 20)
   private ShopStatus status = ShopStatus.PENDING_VERIFICATION;

   @Column(name = "block_reason", length = 300)
   private String blockReason;

   @Column(name = "verified_at")
   private Instant verifiedAt;

   @Column(name = "open_from", nullable = false)
   private LocalTime openFrom = LocalTime.of(8, 0);

   @Column(name = "open_to", nullable = false)
   private LocalTime openTo = LocalTime.of(17, 0);

   @Column(name = "work_days", nullable = false)
   private short workDays = WeekDays.ALL;

   @Column(name = "is_open", nullable = false)
   private boolean open = true;

   @Column(length = 16)
   private String phone;

   @Column(name = "phone_visible", nullable = false)
   private boolean phoneVisible;

   @Column(nullable = false, precision = 2, scale = 1)
   private BigDecimal rating = BigDecimal.ZERO;

   @Column(name = "reviews_count", nullable = false)
   private int reviewsCount;

   @ElementCollection
   @CollectionTable(name = "shop_brands", joinColumns = @JoinColumn(name = "shop_id"))
   @Column(name = "brand_id")
   private Set<Long> brandIds = new HashSet<>();

   @ElementCollection
   @CollectionTable(name = "shop_categories", joinColumns = @JoinColumn(name = "shop_id"))
   @Column(name = "category_id")
   private Set<Long> categoryIds = new HashSet<>();

   @Column(name = "avatar_media_id")
   private Long avatarMediaId;

   /** Фото места по порядку (id media), первое — «Обложка»; до 8 (экран 22). */
   @ElementCollection
   @CollectionTable(name = "shop_photos", joinColumns = @JoinColumn(name = "shop_id"))
   @OrderColumn(name = "sort")
   @Column(name = "media_id", nullable = false)
   private List<Long> photoIds = new ArrayList<>();

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt = Instant.now();

   protected Shop() {
   }

   public Shop(Long ownerId, String name, Long containerId) {
      this.ownerId = ownerId;
      this.name = name;
      this.containerId = containerId;
   }

   @PreUpdate
   void touch() {
      updatedAt = Instant.now();
   }

   /** Проверка пройдена: новый магазин становится действующим, при переезде — занимает новое место. */
   public void verified() {
      if (pendingContainerId != null) {
         containerId = pendingContainerId;
         pendingContainerId = null;
      }
      if (status == ShopStatus.PENDING_VERIFICATION) {
         status = ShopStatus.ACTIVE;
      }
      verifiedAt = Instant.now();
   }

   /** Непроверенный магазин выбрал другой контейнер — место меняется сразу, проверка будет уже на нём. */
   public void moveUnverified(Long containerId) {
      if (status != ShopStatus.PENDING_VERIFICATION) {
         throw new IllegalStateException("Проверенный магазин переезжает через pendingContainerId");
      }
      this.containerId = containerId;
   }

   /** Контейнер, который сейчас надо подтвердить; null — подтверждать нечего. */
   public Long containerToVerify() {
      if (pendingContainerId != null) {
         return pendingContainerId;
      }
      return status == ShopStatus.PENDING_VERIFICATION ? containerId : null;
   }

   public void block(String reason) {
      status = ShopStatus.BLOCKED;
      blockReason = reason;
   }

   /** Снятие блокировки: проверенный магазин снова действует, непроверенный — снова на проверке. */
   public void unblock() {
      status = verifiedAt == null ? ShopStatus.PENDING_VERIFICATION : ShopStatus.ACTIVE;
      blockReason = null;
   }

   /** Пересчёт после нового отзыва: средняя с одним знаком после запятой. */
   public void updateRating(BigDecimal rating, int reviewsCount) {
      this.rating = rating;
      this.reviewsCount = reviewsCount;
   }

   public boolean isActive() {
      return status == ShopStatus.ACTIVE;
   }

   public Set<DayOfWeek> workDaySet() {
      return WeekDays.fromMask(workDays);
   }

   public void setSchedule(LocalTime from, LocalTime to, Set<DayOfWeek> days) {
      if (from != null) {
         openFrom = from;
      }
      if (to != null) {
         openTo = to;
      }
      if (days != null) {
         workDays = WeekDays.toMask(days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days));
      }
   }

   public Long getId() {
      return id;
   }

   public Long getOwnerId() {
      return ownerId;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public Long getContainerId() {
      return containerId;
   }

   public Long getPendingContainerId() {
      return pendingContainerId;
   }

   public void setPendingContainerId(Long pendingContainerId) {
      this.pendingContainerId = pendingContainerId;
   }

   public ShopStatus getStatus() {
      return status;
   }

   public String getBlockReason() {
      return blockReason;
   }

   public Instant getVerifiedAt() {
      return verifiedAt;
   }

   public LocalTime getOpenFrom() {
      return openFrom;
   }

   public LocalTime getOpenTo() {
      return openTo;
   }

   public short getWorkDays() {
      return workDays;
   }

   public boolean isOpen() {
      return open;
   }

   public void setOpen(boolean open) {
      this.open = open;
   }

   public String getPhone() {
      return phone;
   }

   public void setPhone(String phone) {
      this.phone = phone;
   }

   public boolean isPhoneVisible() {
      return phoneVisible;
   }

   public void setPhoneVisible(boolean phoneVisible) {
      this.phoneVisible = phoneVisible;
   }

   public BigDecimal getRating() {
      return rating;
   }

   public int getReviewsCount() {
      return reviewsCount;
   }

   public Set<Long> getBrandIds() {
      return brandIds;
   }

   public Set<Long> getCategoryIds() {
      return categoryIds;
   }

   public Long getAvatarMediaId() {
      return avatarMediaId;
   }

   public void setAvatarMediaId(Long avatarMediaId) {
      this.avatarMediaId = avatarMediaId;
   }

   public List<Long> getPhotoIds() {
      return photoIds;
   }

   public void replacePhotos(List<Long> ids) {
      photoIds.clear();
      photoIds.addAll(ids);
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
