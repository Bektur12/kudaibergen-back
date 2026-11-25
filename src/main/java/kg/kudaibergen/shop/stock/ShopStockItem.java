package kg.kudaibergen.shop.stock;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * «Всегда есть» у магазина: деталь из справочника подсказок (part_hints) на марку и модель.
 * brandId null — на все марки магазина, modelId null — на все модели марки.
 */
@Entity
@Table(name = "shop_stock")
public class ShopStockItem {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "shop_id", nullable = false, updatable = false)
   private Long shopId;

   @Column(name = "hint_id", nullable = false, updatable = false)
   private Long hintId;

   @Column(name = "brand_id", updatable = false)
   private Long brandId;

   @Column(name = "model_id", updatable = false)
   private Long modelId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8, updatable = false)
   private StockSource source;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected ShopStockItem() {
   }

   public ShopStockItem(Long shopId, StockKey key, StockSource source) {
      this.shopId = shopId;
      this.hintId = key.hintId();
      this.brandId = key.brandId();
      this.modelId = key.modelId();
      this.source = source;
   }

   public StockKey key() {
      return new StockKey(hintId, brandId, modelId);
   }

   public Long getId() {
      return id;
   }

   public Long getShopId() {
      return shopId;
   }

   public Long getHintId() {
      return hintId;
   }

   public Long getBrandId() {
      return brandId;
   }

   public Long getModelId() {
      return modelId;
   }

   public StockSource getSource() {
      return source;
   }
}
