package kg.kudaibergen.store.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

@Entity
@Table(name = "stores")
public class Store {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "owner_user_id", nullable = false)
   private User owner;

   /** Только для чтения: id владельца без разворачивания ленивого прокси. */
   @Column(name = "owner_user_id", insertable = false, updatable = false)
   private Long ownerUserId;

   @Column(nullable = false, length = 120)
   private String name;

   @Column(name = "business_type", nullable = false, length = 40)
   private String businessType;

   @Column(columnDefinition = "text")
   private String description;

   @Enumerated(EnumType.STRING)
   @Column(name = "verification_status", nullable = false, length = 40)
   private VerificationStatus verificationStatus = VerificationStatus.NEW;

   @Column(nullable = false, precision = 2, scale = 1)
   private BigDecimal rating = BigDecimal.ZERO;

   @Column(name = "review_count", nullable = false)
   private int reviewCount;

   @Column(name = "total_deals", nullable = false)
   private int totalDeals;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   /** Категории, которыми магазин торгует — по ним запрос находит получателей. */
   @ElementCollection(fetch = FetchType.LAZY)
   @CollectionTable(name = "store_categories", joinColumns = @JoinColumn(name = "store_id"))
   @Column(name = "category", length = 40, nullable = false)
   @Enumerated(EnumType.STRING)
   private Set<PartCategory> categories = EnumSet.noneOf(PartCategory.class);

   protected Store() {
   }

   public Store(User owner, String name, String businessType) {
      this.owner = owner;
      this.name = name;
      this.businessType = businessType;
   }

   public Long getId() {
      return id;
   }

   public User getOwner() {
      return owner;
   }

   public Long getOwnerUserId() {
      return ownerUserId;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getBusinessType() {
      return businessType;
   }

   public void setBusinessType(String businessType) {
      this.businessType = businessType;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public VerificationStatus getVerificationStatus() {
      return verificationStatus;
   }

   public void setVerificationStatus(VerificationStatus verificationStatus) {
      this.verificationStatus = verificationStatus;
   }

   public BigDecimal getRating() {
      return rating;
   }

   public void setRating(BigDecimal rating) {
      this.rating = rating;
   }

   public int getReviewCount() {
      return reviewCount;
   }

   public void setReviewCount(int reviewCount) {
      this.reviewCount = reviewCount;
   }

   public int getTotalDeals() {
      return totalDeals;
   }

   public void incrementDeals() {
      this.totalDeals++;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Set<PartCategory> getCategories() {
      return categories;
   }

   public void setCategories(Set<PartCategory> categories) {
      this.categories.clear();
      this.categories.addAll(categories);
   }
}
