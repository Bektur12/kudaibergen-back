package kg.kudaibergen.master.entity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.garage.entity.FuelType;
import kg.kudaibergen.request.entity.RequestStatus;

/**
 * Заявка на услугу (экраны 33–37): что нужно, какая машина (снимком), что случилось, когда и где.
 * Живёт до expiresAt, потом EXPIRED; продлевается до {@link #MAX_EXTENSIONS} раз, радиус расширяется.
 */
@Entity
@Table(name = "service_requests")
public class ServiceRequest {

   public static final int MAX_EXTENSIONS = 3;
   public static final int MAX_RADIUS_KM = 50;
   public static final int WIDEN_STEP_KM = 5;

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "buyer_id", nullable = false, updatable = false)
   private Long buyerId;

   @Column(nullable = false, updatable = false, length = 20)
   private String service;

   @Column(name = "car_id")
   private Long carId;

   @Column(name = "brand_id", nullable = false, updatable = false)
   private Long brandId;

   @Column(name = "model_id", updatable = false)
   private Long modelId;

   @Column(updatable = false)
   private Short year;

   @Column(name = "engine_volume", updatable = false, precision = 2, scale = 1)
   private BigDecimal engineVolume;

   @Enumerated(EnumType.STRING)
   @Column(updatable = false, length = 10)
   private FuelType fuel;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, updatable = false, length = 7)
   private CarOrigin origin;

   @Column(nullable = false, updatable = false, length = 500)
   private String description;

   @Enumerated(EnumType.STRING)
   @Column(name = "when_kind", nullable = false, updatable = false, length = 8)
   private ServiceWhen when;

   @Column(name = "at_time", updatable = false)
   private Instant atTime;

   @Enumerated(EnumType.STRING)
   @Column(name = "where_kind", nullable = false, updatable = false, length = 12)
   private ServiceWhere where;

   @Column(nullable = false, updatable = false)
   private double lat;

   @Column(nullable = false, updatable = false)
   private double lng;

   @Column(updatable = false, length = 160)
   private String address;

   @Column(name = "radius_km", nullable = false)
   private short radiusKm;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 7)
   private RequestStatus status = RequestStatus.ACTIVE;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 10)
   private ServiceDuration duration;

   @Column(name = "sent_at", nullable = false)
   private Instant sentAt;

   @Column(name = "expires_at", nullable = false)
   private Instant expiresAt;

   @Column(name = "extended_times", nullable = false)
   private short extendedTimes;

   @Column(name = "recipients_count", nullable = false)
   private int recipientsCount;

   @Column(name = "can_help_count", nullable = false)
   private int canHelpCount;

   @Column(name = "closed_with_master_id")
   private Long closedWithMasterId;

   @Column(name = "closed_at")
   private Instant closedAt;

   @Column(name = "created_at", nullable = false, updatable = false)
   private Instant createdAt;

   /** Фото к заявке по порядку, до 5. */
   @ElementCollection
   @CollectionTable(name = "service_request_photos", joinColumns = @JoinColumn(name = "request_id"))
   @OrderColumn(name = "sort")
   @Column(name = "media_id", nullable = false)
   private List<Long> photoIds = new ArrayList<>();

   /** Скрыто администрацией (модерация). Пишет только админка, JPA поле не меняет. */
   @Column(name = "hidden_by_admin", insertable = false, updatable = false)
   private boolean hiddenByAdmin;

   @Column(name = "hidden_reason", insertable = false, updatable = false)
   private String hiddenReason;

   protected ServiceRequest() {
   }

   public ServiceRequest(Long buyerId, String service, CarSnapshot car, String description, ServiceWhen when,
                         Instant atTime, ServiceWhere where, double lat, double lng, String address, int radiusKm,
                         List<Long> photoIds, ServiceDuration duration, Instant now) {
      this.buyerId = buyerId;
      this.service = service;
      this.carId = car.carId();
      this.brandId = car.brandId();
      this.modelId = car.modelId();
      this.year = car.year();
      this.engineVolume = car.engineVolume();
      this.fuel = car.fuel();
      this.origin = car.origin();
      this.description = description;
      this.when = when;
      this.atTime = atTime;
      this.where = where;
      this.lat = lat;
      this.lng = lng;
      this.address = address;
      this.radiusKm = (short) radiusKm;
      this.photoIds = new ArrayList<>(photoIds);
      this.duration = duration;
      this.sentAt = now;
      this.expiresAt = duration.expiresAt(now);
      this.createdAt = now;
   }

   /** Машина на момент заявки: из гаража или введённая на шагах 2–3. */
   public record CarSnapshot(Long carId, Long brandId, Long modelId, Short year, BigDecimal engineVolume,
                             FuelType fuel, CarOrigin origin) {
   }

   public boolean isActive() {
      return status == RequestStatus.ACTIVE;
   }

   public boolean isOpen() {
      return status != RequestStatus.CLOSED;
   }

   public boolean canExtend() {
      return isOpen() && extendedTimes < MAX_EXTENSIONS;
   }

   public boolean canWiden() {
      return isOpen() && radiusKm < MAX_RADIUS_KM;
   }

   public void dispatched(int added) {
      recipientsCount += added;
   }

   public void canHelpChanged(int delta) {
      canHelpCount = Math.max(0, canHelpCount + delta);
   }

   /** «Расширить радиус» (+5 км, 37): срок заново, истёкшая заявка снова активна. */
   public void widen(Instant now) {
      radiusKm = (short) Math.min(MAX_RADIUS_KM, radiusKm + WIDEN_STEP_KM);
      restart(now, duration.expiresAt(now));
   }

   /** «Продлить»: активной — сдвиг срока, истёкшей — снова активна на {@code by} от сейчас. */
   public void extend(Duration by, Instant now) {
      extendedTimes++;
      if (isActive()) {
         expiresAt = expiresAt.plus(by);
      } else {
         restart(now, now.plus(by));
      }
   }

   private void restart(Instant now, Instant until) {
      status = RequestStatus.ACTIVE;
      closedAt = null;
      sentAt = now;
      expiresAt = until;
   }

   public void close(Long masterId, Instant now) {
      status = RequestStatus.CLOSED;
      closedWithMasterId = masterId;
      closedAt = now;
   }

   public void expire(Instant now) {
      status = RequestStatus.EXPIRED;
      closedAt = now;
   }

   public Long getId() {
      return id;
   }

   public Long getBuyerId() {
      return buyerId;
   }

   public String getService() {
      return service;
   }

   public Long getCarId() {
      return carId;
   }

   public Long getBrandId() {
      return brandId;
   }

   public Long getModelId() {
      return modelId;
   }

   public Short getYear() {
      return year;
   }

   public BigDecimal getEngineVolume() {
      return engineVolume;
   }

   public FuelType getFuel() {
      return fuel;
   }

   public CarOrigin getOrigin() {
      return origin;
   }

   public String getDescription() {
      return description;
   }

   public ServiceWhen getWhen() {
      return when;
   }

   public Instant getAtTime() {
      return atTime;
   }

   public ServiceWhere getWhere() {
      return where;
   }

   public double getLat() {
      return lat;
   }

   public double getLng() {
      return lng;
   }

   public String getAddress() {
      return address;
   }

   public int getRadiusKm() {
      return radiusKm;
   }

   public RequestStatus getStatus() {
      return status;
   }

   public ServiceDuration getDuration() {
      return duration;
   }

   public Instant getSentAt() {
      return sentAt;
   }

   public Instant getExpiresAt() {
      return expiresAt;
   }

   public int getExtendedTimes() {
      return extendedTimes;
   }

   public int getRecipientsCount() {
      return recipientsCount;
   }

   public int getCanHelpCount() {
      return canHelpCount;
   }

   public Long getClosedWithMasterId() {
      return closedWithMasterId;
   }

   public Instant getClosedAt() {
      return closedAt;
   }

   public Instant getCreatedAt() {
      return createdAt;
   }

   public List<Long> getPhotoIds() {
      return List.copyOf(photoIds);
   }

   public boolean isHiddenByAdmin() {
      return hiddenByAdmin;
   }

   public String getHiddenReason() {
      return hiddenReason;
   }
}
