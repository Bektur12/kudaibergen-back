package kg.kudaibergen.notification.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Исходящее уведомление. Пуш в HTTP-потоке не шлём — только строка в таблице. */
@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "user_id", nullable = false)
   private Long userId;

   @Column(nullable = false, length = 120)
   private String title;

   @Column(nullable = false, length = 400)
   private String body;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(name = "payload")
   private String payload;

   @Column(name = "sent_at")
   private Instant sentAt;

   @Column(nullable = false)
   private short attempts;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected NotificationOutbox() {
   }

   public NotificationOutbox(Long userId, String title, String body, String payload) {
      this.userId = userId;
      this.title = title;
      this.body = body;
      this.payload = payload;
      this.createdAt = Instant.now();
   }

   public void markSent() {
      this.sentAt = Instant.now();
   }

   public void registerAttempt() {
      this.attempts++;
   }

   public Long getId() {
      return id;
   }

   public Long getUserId() {
      return userId;
   }

   public String getTitle() {
      return title;
   }

   public String getBody() {
      return body;
   }

   public String getPayload() {
      return payload;
   }

   public Instant getSentAt() {
      return sentAt;
   }

   public short getAttempts() {
      return attempts;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
