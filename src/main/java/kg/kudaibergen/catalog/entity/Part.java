package kg.kudaibergen.catalog.entity;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import kg.kudaibergen.common.web.PublicIds;
import kg.kudaibergen.request.entity.PartCondition;

/** Запчасть в каталоге продавца (экраны 24, 26, 27, 29). */
@Entity
@Table(name = "parts")
public class Part {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "shop_id", nullable = false, updatable = false)
   private Long shopId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8)
   private PartStatus status = PartStatus.DRAFT;

   @Column(length = 120)
   private String title;

   @Column(name = "category_id")
   private Long categoryId;

   @Enumerated(EnumType.STRING)
   @Column(length = 9)
   private PartCondition condition;

   private Integer price;

   @Column(nullable = false)
   private int quantity = 1;

   @Column(length = 60)
   private String manufacturer;

   @Column(name = "oem_number", length = 40)
   private String oemNumber;

   @Column(name = "oem_norm", length = 40)
   private String oemNorm;

   @Enumerated(EnumType.STRING)
   @Column(length = 5)
   private PartSide side;

   @Enumerated(EnumType.STRING)
   @Column(length = 5)
   private PartPosition position;

   @Column(name = "views_count", nullable = false)
   private int viewsCount;

   @Column(name = "published_at")
   private Instant publishedAt;

   /** id фото (media) по порядку; первое — «Главное». */
   @ElementCollection
   @CollectionTable(name = "part_photos", joinColumns = @JoinColumn(name = "part_id"))
   @OrderColumn(name = "sort")
   @Column(name = "media_id", nullable = false)
   private List<Long> photoIds = new ArrayList<>();

   @ElementCollection
   @CollectionTable(name = "part_fitments", joinColumns = @JoinColumn(name = "part_id"))
   private List<Fitment> fitments = new ArrayList<>();

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   @Column(name = "updated_at", nullable = false)
   private Instant updatedAt = Instant.now();

   /** Скрыто администрацией (модерация). Пишет только админка, JPA поле не меняет. */
   @Column(name = "hidden_by_admin", insertable = false, updatable = false)
   private boolean hiddenByAdmin;

   @Column(name = "hidden_reason", insertable = false, updatable = false)
   private String hiddenReason;

   protected Part() {
   }

   /** Для ссылок «Поделиться»: не перебирается, в отличие от id. */
   @Column(name = "public_id", nullable = false, updatable = false, length = 16)
   private String publicId = PublicIds.next();

   public Part(Long shopId) {
      this.shopId = shopId;
   }

   public String getPublicId() {
      return publicId;
   }

   @PreUpdate
   void touch() {
      updatedAt = Instant.now();
   }

   public void publish(Instant now) {
      status = PartStatus.ACTIVE;
      if (publishedAt == null) {
         publishedAt = now;
      }
   }

   public boolean isActive() {
      return status == PartStatus.ACTIVE;
   }

   public boolean inStock() {
      return quantity > 0;
   }

   /** Номер детали для поиска: без пробелов, дефисов и точек, заглавными. */
   public static String normalizeOem(String oem) {
      if (oem == null) {
         return null;
      }
      String norm = oem.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase();
      return norm.isEmpty() ? null : norm;
   }

   public void setOemNumber(String oemNumber) {
      this.oemNumber = oemNumber;
      this.oemNorm = normalizeOem(oemNumber);
   }

   public void replacePhotos(List<Long> ids) {
      photoIds.clear();
      photoIds.addAll(ids);
   }

   public void replaceFitments(List<Fitment> list) {
      fitments.clear();
      fitments.addAll(list);
   }

   public Long getId() {
      return id;
   }

   public Long getShopId() {
      return shopId;
   }

   public PartStatus getStatus() {
      return status;
   }

   public void setStatus(PartStatus status) {
      this.status = status;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public Long getCategoryId() {
      return categoryId;
   }

   public void setCategoryId(Long categoryId) {
      this.categoryId = categoryId;
   }

   public PartCondition getCondition() {
      return condition;
   }

   public void setCondition(PartCondition condition) {
      this.condition = condition;
   }

   public Integer getPrice() {
      return price;
   }

   public void setPrice(Integer price) {
      this.price = price;
   }

   public int getQuantity() {
      return quantity;
   }

   public void setQuantity(int quantity) {
      this.quantity = quantity;
   }

   public String getManufacturer() {
      return manufacturer;
   }

   public void setManufacturer(String manufacturer) {
      this.manufacturer = manufacturer;
   }

   public String getOemNumber() {
      return oemNumber;
   }

   public PartSide getSide() {
      return side;
   }

   public void setSide(PartSide side) {
      this.side = side;
   }

   public PartPosition getPosition() {
      return position;
   }

   public void setPosition(PartPosition position) {
      this.position = position;
   }

   public int getViewsCount() {
      return viewsCount;
   }

   public Instant getPublishedAt() {
      return publishedAt;
   }

   public List<Long> getPhotoIds() {
      return photoIds;
   }

   public List<Fitment> getFitments() {
      return fitments;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public Instant getUpdatedAt() {
      return updatedAt;
   }

   public boolean isHiddenByAdmin() {
      return hiddenByAdmin;
   }

   public String getHiddenReason() {
      return hiddenReason;
   }
}
