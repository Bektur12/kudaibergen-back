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

   @Column(name = "key_1080", nullable = false, length = 300, updatable = false)
   private String key1080;

   @Column(name = "key_320", nullable = false, length = 300, updatable = false)
   private String key320;

   @Column(nullable = false, updatable = false)
   private int width;

   @Column(nullable = false, updatable = false)
   private int height;

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

   public int getWidth() {
      return width;
   }

   public int getHeight() {
      return height;
   }

   public long getSizeBytes() {
      return sizeBytes;
   }
}
