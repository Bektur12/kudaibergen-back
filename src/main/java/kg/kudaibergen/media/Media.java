package kg.kudaibergen.media;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Загруженное фото в двух размерах: 1080 px (карточка) и 320 px (сетка, превью). */
@Entity
@Table(name = "media")
public class Media {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "owner_id", updatable = false)
   private Long ownerId;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 8, updatable = false)
   private MediaPurpose purpose;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 5, updatable = false)
   private MediaKind kind = MediaKind.PHOTO;

   /** Фото — 1080 px; у видео — обложка (может не быть). */
   @Column(name = "key_1080", length = 300, updatable = false)
   private String key1080;

   @Column(name = "key_320", length = 300, updatable = false)
   private String key320;

   @Column(updatable = false)
   private Integer width;

   @Column(updatable = false)
   private Integer height;

   @Column(name = "video_key", length = 300, updatable = false)
   private String videoKey;

   @Column(name = "mime_type", length = 60, updatable = false)
   private String mimeType;

   @Column(name = "duration_sec", updatable = false)
   private Short durationSec;

   @Column(name = "size_bytes", nullable = false, updatable = false)
   private long sizeBytes;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt = Instant.now();

   protected Media() {
   }

   public Media(Long ownerId, MediaPurpose purpose, String key1080, String key320, int width, int height,
                long sizeBytes) {
      this.ownerId = ownerId;
      this.purpose = purpose;
      this.key1080 = key1080;
      this.key320 = key320;
      this.width = width;
      this.height = height;
      this.sizeBytes = sizeBytes;
   }

   /** Видео; poster — обложка, пережатая как фото (null — без обложки). */
   public static Media video(Long ownerId, MediaPurpose purpose, String videoKey, String mimeType, int durationSec,
                             long sizeBytes, String posterKey1080, String posterKey320, Integer posterWidth,
                             Integer posterHeight) {
      Media media = new Media();
      media.ownerId = ownerId;
      media.purpose = purpose;
      media.kind = MediaKind.VIDEO;
      media.videoKey = videoKey;
      media.mimeType = mimeType;
      media.durationSec = (short) durationSec;
      media.sizeBytes = sizeBytes;
      media.key1080 = posterKey1080;
      media.key320 = posterKey320;
      media.width = posterWidth;
      media.height = posterHeight;
      return media;
   }

   public boolean isVideo() {
      return kind == MediaKind.VIDEO;
   }

   public MediaKind getKind() {
      return kind;
   }

   public String getVideoKey() {
      return videoKey;
   }

   public String getMimeType() {
      return mimeType;
   }

   public Integer getDurationSec() {
      return durationSec == null ? null : durationSec.intValue();
   }

   public Long getId() {
      return id;
   }

   public Long getOwnerId() {
      return ownerId;
   }

   public MediaPurpose getPurpose() {
      return purpose;
   }

   public String getKey1080() {
      return key1080;
   }

   public String getKey320() {
      return key320;
   }

   public Integer getWidth() {
      return width;
   }

   public Integer getHeight() {
      return height;
   }

   public long getSizeBytes() {
      return sizeBytes;
   }
}
