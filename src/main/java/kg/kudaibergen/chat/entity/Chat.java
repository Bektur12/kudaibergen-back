package kg.kudaibergen.chat.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Чат появляется, когда покупатель принял предложение. */
@Entity
@Table(name = "chats")
public class Chat {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "request_id")
   private Long requestId;

   @Column(name = "buyer_id", nullable = false)
   private Long buyerId;

   @Column(name = "store_id", nullable = false)
   private Long storeId;

   @Column(name = "last_message", columnDefinition = "text")
   private String lastMessage;

   @Column(name = "last_message_at")
   private Instant lastMessageAt;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected Chat() {
   }

   public Chat(Long buyerId, Long storeId, Long requestId) {
      this.buyerId = buyerId;
      this.storeId = storeId;
      this.requestId = requestId;
   }

   public void touch(String message, Instant at) {
      this.lastMessage = message;
      this.lastMessageAt = at;
   }

   public Long getId() {
      return id;
   }

   public Long getRequestId() {
      return requestId;
   }

   public Long getBuyerId() {
      return buyerId;
   }

   public Long getStoreId() {
      return storeId;
   }

   public String getLastMessage() {
      return lastMessage;
   }

   public Instant getLastMessageAt() {
      return lastMessageAt;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
