package kg.kudaibergen.chat.entity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Сообщение чата. media_key — ключ в хранилище, URL клиенту собирает сервер при выдаче. */
@Entity
@Table(name = "messages")
public class Message {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "chat_id", nullable = false, updatable = false)
   private Long chatId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 6, updatable = false)
   private ChatSide side;

   @Column(name = "sender_id", updatable = false)
   private Long senderId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8, updatable = false)
   private MessageType type;

   @Column(length = 4000, updatable = false)
   private String text;

   @Column(length = 20, updatable = false)
   private String code;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(updatable = false)
   private Map<String, Object> payload;

   @Column(name = "media_key", length = 300, updatable = false)
   private String mediaKey;

   @Column(name = "mime_type", length = 100, updatable = false)
   private String mimeType;

   @Column(name = "duration_seconds", updatable = false)
   private Integer durationSeconds;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(updatable = false)
   private List<Double> waveform;

   @Column(name = "client_id", length = 64, updatable = false)
   private String clientId;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   protected Message() {
   }

   private Message(Long chatId, ChatSide side, Long senderId, MessageType type, String clientId, Instant now) {
      this.chatId = chatId;
      this.side = side;
      this.senderId = senderId;
      this.type = type;
      this.clientId = clientId;
      this.createdAt = now;
   }

   public static Message text(Long chatId, ChatSide side, Long senderId, String text, String clientId, Instant now) {
      Message message = new Message(chatId, side, senderId, MessageType.TEXT, clientId, now);
      message.text = text;
      return message;
   }

   public static Message media(Long chatId, ChatSide side, Long senderId, MessageType type, String caption,
                               String mediaKey, String mimeType, Integer durationSeconds, List<Double> waveform,
                               String clientId, Instant now) {
      Message message = new Message(chatId, side, senderId, type, clientId, now);
      message.text = caption;
      message.mediaKey = mediaKey;
      message.mimeType = mimeType;
      message.durationSeconds = durationSeconds;
      message.waveform = waveform;
      return message;
   }

   public static Message quick(Long chatId, ChatSide side, Long senderId, QuickReply reply, String text,
                               Map<String, Object> payload, String clientId, Instant now) {
      Message message = new Message(chatId, side, senderId, MessageType.QUICK, clientId, now);
      message.code = reply.name();
      message.text = text;
      message.payload = payload;
      return message;
   }

   /** Карточка ответа «Есть» от имени бокса — первое сообщение чата по запросу. */
   public static Message reply(Long chatId, Long senderId, String text, Map<String, Object> payload, Instant now) {
      Message message = new Message(chatId, ChatSide.SHOP, senderId, MessageType.REPLY, null, now);
      message.text = text;
      message.payload = payload;
      return message;
   }

   public static Message system(Long chatId, SystemEvent event, Map<String, Object> payload, Instant now) {
      Message message = new Message(chatId, ChatSide.SYSTEM, null, MessageType.SYSTEM, null, now);
      message.code = event.name();
      message.payload = payload;
      return message;
   }

   public Long getId() {
      return id;
   }

   public Long getChatId() {
      return chatId;
   }

   public ChatSide getSide() {
      return side;
   }

   public Long getSenderId() {
      return senderId;
   }

   public MessageType getType() {
      return type;
   }

   public String getText() {
      return text;
   }

   public String getCode() {
      return code;
   }

   public Map<String, Object> getPayload() {
      return payload;
   }

   public String getMediaKey() {
      return mediaKey;
   }

   public String getMimeType() {
      return mimeType;
   }

   public Integer getDurationSeconds() {
      return durationSeconds;
   }

   public List<Double> getWaveform() {
      return waveform;
   }

   public String getClientId() {
      return clientId;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }
}
