package kg.kudaibergen.master.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
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
import kg.kudaibergen.common.web.PublicIds;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.shop.entity.ShopStatus;

/**
 * Мастер или СТО (экран 38): какие услуги, с какими марками и машинами работает, где и в каком радиусе.
 * Один на пользователя; тот же человек может быть и продавцом на рынке.
 */
@Entity
@Table(name = "masters")
public class Master {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "owner_id", nullable = false, updatable = false)
   private Long ownerId;

   @Column(nullable = false, length = 60)
   private String name;

   @Column(name = "avatar_media_id")
   private Long avatarMediaId;

   @Column(length = 16)
   private String phone;

   @Column(nullable = false, length = 160)
   private String address;

   @Column(nullable = false)
   private double lat;

   @Column(nullable = false)
   private double lng;

   @Column(name = "radius_km", nullable = false)
   private short radiusKm = 5;

   @Column(name = "is_mobile", nullable = false)
   private boolean mobile;

   @Column(name = "all_brands", nullable = false)
   private boolean allBrands;

   @Column(name = "open_from", nullable = false)
   private LocalTime openFrom = LocalTime.of(9, 0);

   @Column(name = "open_to", nullable = false)
   private LocalTime openTo = LocalTime.of(19, 0);

   @Column(name = "work_days", nullable = false)
   private short workDays = 127;

   @Column(name = "is_accepting", nullable = false)
   private boolean accepting = true;

   @Column(nullable = false, precision = 2, scale = 1)
   private BigDecimal rating = BigDecimal.ZERO;

   @Column(name = "reviews_count", nullable = false)
   private int reviewsCount;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 20)
   private ShopStatus status = ShopStatus.ACTIVE;

   @Column(name = "block_reason", length = 300)
   private String blockReason;

   @Column(name = "container_id")
   private Long containerId;

   /** Для ссылок «Поделиться»: не перебирается, в отличие от id. */
   @Column(name = "public_id", nullable = false, updatable = false, length = 16)
   private String publicId = PublicIds.next();

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt = Instant.now();

   @ElementCollection
   @CollectionTable(name = "master_services", joinColumns = @JoinColumn(name = "master_id"))
   @Column(name = "service", nullable = false, length = 20)
   private Set<String> services = new HashSet<>();

   @ElementCollection
   @CollectionTable(name = "master_brands", joinColumns = @JoinColumn(name = "master_id"))
   @Column(name = "brand_id", nullable = false)
   private Set<Long> brandIds = new HashSet<>();

   /** Пусто — любые машины. */
   @ElementCollection
   @CollectionTable(name = "master_origins", joinColumns = @JoinColumn(name = "master_id"))
   @Enumerated(EnumType.STRING)
   @Column(name = "origin", nullable = false, length = 7)
   private Set<CarOrigin> origins = EnumSet.noneOf(CarOrigin.class);

   /** Фото работ и места по порядку, первое — обложка. */
   @ElementCollection
   @CollectionTable(name = "master_photos", joinColumns = @JoinColumn(name = "master_id"))
   @OrderColumn(name = "sort")
   @Column(name = "media_id", nullable = false)
   private List<Long> photoIds = new ArrayList<>();

   protected Master() {
   }

   public Master(Long ownerId, String name, String address, double lat, double lng) {
      this.ownerId = ownerId;
      this.name = name;
      this.address = address;
      this.lat = lat;
      this.lng = lng;
   }

   @PreUpdate
   void touch() {
      updatedAt = Instant.now();
   }

   public boolean isActive() {
      return status == ShopStatus.ACTIVE;
   }

   /** Работает ли с машиной этой марки и страны. Страна «не знаю» подходит всем. */
   public boolean worksWith(Long brandId, CarOrigin origin) {
      boolean brand = allBrands || brandIds.contains(brandId);
      boolean country = origins.isEmpty() || origin == null || origin == CarOrigin.UNKNOWN || origins.contains(origin);
      return brand && country;
   }

   public void updateRating(BigDecimal rating, int reviewsCount) {
      this.rating = rating;
      this.reviewsCount = reviewsCount;
   }

   public void replaceServices(Set<String> codes) {
      services.clear();
      services.addAll(codes);
   }

   public void replaceBrands(boolean all, Set<Long> ids) {
      allBrands = all;
      brandIds.clear();
      if (!all) {
         brandIds.addAll(ids);
      }
   }

   public void replaceOrigins(Set<CarOrigin> values) {
      origins.clear();
      values.stream().filter(origin -> origin != CarOrigin.UNKNOWN).forEach(origins::add);
   }

   public void replacePhotos(List<Long> ids) {
      photoIds.clear();
      photoIds.addAll(ids);
   }

   public void pending() {
      status = ShopStatus.PENDING_VERIFICATION;
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

   public Long getAvatarMediaId() {
      return avatarMediaId;
   }

   public void setAvatarMediaId(Long avatarMediaId) {
      this.avatarMediaId = avatarMediaId;
   }

   public String getPhone() {
      return phone;
   }

   public void setPhone(String phone) {
      this.phone = phone;
   }

   public String getAddress() {
      return address;
   }

   public double getLat() {
      return lat;
   }

   public double getLng() {
      return lng;
   }

   public void setLocation(String address, double lat, double lng) {
      this.address = address;
      this.lat = lat;
      this.lng = lng;
   }

   public int getRadiusKm() {
      return radiusKm;
   }

   public void setRadiusKm(int radiusKm) {
      this.radiusKm = (short) radiusKm;
   }

   public boolean isMobile() {
      return mobile;
   }

   public void setMobile(boolean mobile) {
      this.mobile = mobile;
   }

   public boolean isAllBrands() {
      return allBrands;
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

   public void setSchedule(LocalTime from, LocalTime to, short days) {
      this.openFrom = from;
      this.openTo = to;
      this.workDays = days;
   }

   public boolean isAccepting() {
      return accepting;
   }

   public void setAccepting(boolean accepting) {
      this.accepting = accepting;
   }

   public BigDecimal getRating() {
      return rating;
   }

   public int getReviewsCount() {
      return reviewsCount;
   }

   public ShopStatus getStatus() {
      return status;
   }

   public String getBlockReason() {
      return blockReason;
   }

   public Long getContainerId() {
      return containerId;
   }

   public void setContainerId(Long containerId) {
      this.containerId = containerId;
   }

   public String getPublicId() {
      return publicId;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Set<String> getServices() {
      return services;
   }

   public Set<Long> getBrandIds() {
      return brandIds;
   }

   public Set<CarOrigin> getOrigins() {
      return origins;
   }

   public List<Long> getPhotoIds() {
      return photoIds;
   }
}
