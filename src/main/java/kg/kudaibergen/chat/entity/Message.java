package kg.kudaibergen.chat.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class Message {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "chat_id", nullable = false)
   private Long chatId;

   @Column(name = "sender_id", nullable = false)
   private Long senderId;

   @Column(nullable = false, columnDefinition = "text")
   private String body;

   @Column(nullable = false, length = 10)
   private String type = "TEXT";

   @Column(name = "media_url")
   private String mediaUrl;

   @Column(name = "mime_type", length = 100)
   private String mimeType;

   @Column(name = "duration_seconds")
   private Integer durationSeconds;

   @Column(name = "read_at")
   private Instant readAt;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected Message() {
   }

   public Message(Long chatId, Long senderId, String body, String type) {
      this(chatId, senderId, body, type, null, null, null);
   }

   public Message(Long chatId, Long senderId, String body, String type, String mediaUrl, String mimeType,
                  Integer durationSeconds) {
      this.chatId = chatId;
      this.senderId = senderId;
      this.body = body;
      this.type = type == null ? "TEXT" : type;
      this.mediaUrl = mediaUrl;
      this.mimeType = mimeType;
      this.durationSeconds = durationSeconds;
   }

   public Long getId() {
      return id;
   }

   public Long getChatId() {
      return chatId;
   }

   public Long getSenderId() {
      return senderId;
   }

   public String getBody() {
      return body;
   }

   public String getType() {
      return type;
   }

   public String getMediaUrl() {
      return mediaUrl;
   }

   public String getMimeType() {
      return mimeType;
   }

   public Integer getDurationSeconds() {
      return durationSeconds;
   }

   public Instant getReadAt() {
      return readAt;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
