package kg.kudaibergen.request.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kg.kudaibergen.common.PartCategory;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.Vehicle;

/** Запрос покупателя — то, что веером уходит подходящим магазинам. */
@Entity
@Table(name = "requests")
public class Request {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "buyer_id", nullable = false)
   private User buyer;

   @Column(name = "buyer_id", insertable = false, updatable = false)
   private Long buyerId;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "vehicle_id")
   private Vehicle vehicle;

   @Column(name = "car_text", length = 160)
   private String carText;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 40)
   private PartCategory category;

   @Column(nullable = false, columnDefinition = "text")
   private String description;

   @Column(name = "budget_min")
   private Integer budgetMin;

   @Column(name = "budget_max")
   private Integer budgetMax;

   @Column(nullable = false, length = 3)
   private String currency = "KGS";

   @Column(nullable = false, length = 80)
   private String city;

   @Column(name = "is_urgent", nullable = false)
   private boolean urgent;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 40)
   private RequestStatus status = RequestStatus.ACTIVE;

   @Column(name = "offer_count", nullable = false)
   private int offerCount;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "expires_at", nullable = false)
   private Instant expiresAt;

   /** Ключ в медиа-хранилище (как Message.mediaUrl), не URL — резолвится при выдаче наружу. */
   @Column(name = "photo_url")
   private String photoUrl;

   protected Request() {
   }

   public Request(User buyer, PartCategory category, String description, String city) {
      this.buyer = buyer;
      this.category = category;
      this.description = description;
      this.city = city;
   }

   public boolean isActive() {
      return status == RequestStatus.ACTIVE && expiresAt.isAfter(Instant.now());
   }

   public Long getId() {
      return id;
   }

   public User getBuyer() {
      return buyer;
   }

   public Long getBuyerId() {
      return buyerId;
   }

   public Vehicle getVehicle() {
      return vehicle;
   }

   public void setVehicle(Vehicle vehicle) {
      this.vehicle = vehicle;
   }

   public String getCarText() {
      return carText;
   }

   public void setCarText(String carText) {
      this.carText = carText;
   }

   public PartCategory getCategory() {
      return category;
   }

   public String getDescription() {
      return description;
   }

   public Integer getBudgetMin() {
      return budgetMin;
   }

   public void setBudgetMin(Integer budgetMin) {
      this.budgetMin = budgetMin;
   }

   public Integer getBudgetMax() {
      return budgetMax;
   }

   public void setBudgetMax(Integer budgetMax) {
      this.budgetMax = budgetMax;
   }

   public String getCurrency() {
      return currency;
   }

   public void setCurrency(String currency) {
      this.currency = currency;
   }

   public String getCity() {
      return city;
   }

   public boolean isUrgent() {
      return urgent;
   }

   public void setUrgent(boolean urgent) {
      this.urgent = urgent;
   }

   public RequestStatus getStatus() {
      return status;
   }

   public void setStatus(RequestStatus status) {
      this.status = status;
   }

   public int getOfferCount() {
      return offerCount;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getExpiresAt() {
      return expiresAt;
   }

   public void setExpiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
   }

   public String getPhotoUrl() {
      return photoUrl;
   }

   public void setPhotoUrl(String photoUrl) {
      this.photoUrl = photoUrl;
   }
}
