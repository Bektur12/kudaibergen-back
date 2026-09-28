package kg.kudaibergen.chat.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Чат покупателя с магазином (08, 13, 16). Все люди бокса пишут и читают от имени магазина. */
@Entity
@Table(name = "chats")
public class Chat {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "buyer_id", nullable = false, updatable = false)
   private Long buyerId;

   @Column(name = "shop_id", nullable = false, updatable = false)
   private Long shopId;

   @Column(name = "request_id", updatable = false)
   private Long requestId;

   @Column(name = "last_message_id")
   private Long lastMessageId;

   @Column(name = "last_message_at")
   private Instant lastMessageAt;

   @Column(name = "buyer_read_message_id", nullable = false)
   private long buyerReadMessageId;

   @Column(name = "shop_read_message_id", nullable = false)
   private long shopReadMessageId;

   @Column(name = "buyer_first_message_at")
   private Instant buyerFirstMessageAt;

   @Column(name = "blocked_by_buyer", nullable = false)
   private boolean blockedByBuyer;

   @Column(name = "blocked_by_shop", nullable = false)
   private boolean blockedByShop;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   protected Chat() {
   }

   public Chat(Long buyerId, Long shopId, Long requestId, Instant now) {
      this.buyerId = buyerId;
      this.shopId = shopId;
      this.requestId = requestId;
      this.createdAt = now;
   }

   /** Новое сообщение: оно последнее в списке чатов и прочитано своей стороной. */
   public void messageAdded(Message message) {
      lastMessageId = message.getId();
      lastMessageAt = message.getCreatedAt();
      switch (message.getSide()) {
         case BUYER -> {
            buyerReadMessageId = message.getId();
            if (buyerFirstMessageAt == null) {
               buyerFirstMessageAt = message.getCreatedAt();
            }
         }
         case SHOP -> shopReadMessageId = message.getId();
         case SYSTEM -> {
         }
      }
   }

   /** Возвращает true, если отметка сдвинулась. */
   public boolean read(ChatSide side, long upToMessageId) {
      if (side == ChatSide.BUYER && upToMessageId > buyerReadMessageId) {
         buyerReadMessageId = upToMessageId;
         return true;
      }
      if (side == ChatSide.SHOP && upToMessageId > shopReadMessageId) {
         shopReadMessageId = upToMessageId;
         return true;
      }
      return false;
   }

   public long readMessageId(ChatSide side) {
      return side == ChatSide.BUYER ? buyerReadMessageId : shopReadMessageId;
   }

   public void setBlocked(ChatSide by, boolean blocked) {
      if (by == ChatSide.BUYER) {
         blockedByBuyer = blocked;
      } else {
         blockedByShop = blocked;
      }
   }

   public boolean isBlocked() {
      return blockedByBuyer || blockedByShop;
   }

   public boolean isBlockedBy(ChatSide side) {
      return side == ChatSide.BUYER ? blockedByBuyer : blockedByShop;
   }

   public Long getId() {
      return id;
   }

   public Long getBuyerId() {
      return buyerId;
   }

   public Long getShopId() {
      return shopId;
   }

   public Long getRequestId() {
      return requestId;
   }

   public Long getLastMessageId() {
      return lastMessageId;
   }

   public Instant getLastMessageAt() {
      return lastMessageAt;
   }

   public long getBuyerReadMessageId() {
      return buyerReadMessageId;
   }

   public long getShopReadMessageId() {
      return shopReadMessageId;
   }

   public Instant getBuyerFirstMessageAt() {
      return buyerFirstMessageAt;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
