package kg.kudaibergen.chat.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Свой шаблон ответа магазина: общий для владельца и сотрудников, показывается рядом с быстрыми ответами. */
@Entity
@Table(name = "reply_templates")
public class ReplyTemplate {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "shop_id", nullable = false, updatable = false)
   private Long shopId;

   @Column(nullable = false, length = 300)
   private String text;

   @Column(name = "sort_order", nullable = false)
   private short sortOrder;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected ReplyTemplate() {
   }

   public ReplyTemplate(Long shopId, String text, short sortOrder) {
      this.shopId = shopId;
      this.text = text;
      this.sortOrder = sortOrder;
   }

   public Long getId() {
      return id;
   }

   public Long getShopId() {
      return shopId;
   }

   public String getText() {
      return text;
   }

   public void setText(String text) {
      this.text = text;
   }

   public short getSortOrder() {
      return sortOrder;
   }

   public void setSortOrder(short sortOrder) {
      this.sortOrder = sortOrder;
   }
}
