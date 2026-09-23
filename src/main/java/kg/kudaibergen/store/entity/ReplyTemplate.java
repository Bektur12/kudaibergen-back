package kg.kudaibergen.store.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Шаблон быстрого ответа — основа массового ответа на десятки запросов. */
@Entity
@Table(name = "reply_templates")
public class ReplyTemplate {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY, optional = false)
   @JoinColumn(name = "store_id", nullable = false)
   private Store store;

   @Column(nullable = false, length = 60)
   private String title;

   @Column(nullable = false, columnDefinition = "text")
   private String body;

   @Column(name = "sort_order", nullable = false)
   private short sortOrder;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected ReplyTemplate() {
   }

   public ReplyTemplate(Store store) {
      this.store = store;
   }

   public Long getId() {
      return id;
   }

   public Store getStore() {
      return store;
   }

   public String getTitle() {
      return title;
   }

   public void setTitle(String title) {
      this.title = title;
   }

   public String getBody() {
      return body;
   }

   public void setBody(String body) {
      this.body = body;
   }

   public short getSortOrder() {
      return sortOrder;
   }

   public void setSortOrder(short sortOrder) {
      this.sortOrder = sortOrder;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
