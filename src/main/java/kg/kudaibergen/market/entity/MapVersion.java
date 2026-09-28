package kg.kudaibergen.market.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Версия схемы рынка. JSON-поля хранятся строками и разбираются в MarketMapService. */
@Entity
@Table(name = "map_versions")
public class MapVersion {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false, unique = true)
   private int version;

   @Column(name = "is_current", nullable = false)
   private boolean current;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String boundary;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String blocks;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String passages;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String entrances;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String pois;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String streets;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(nullable = false)
   private String labels;

   @Column(name = "meters_per_px", nullable = false, precision = 8, scale = 4)
   private BigDecimal metersPerPx;

   @JdbcTypeCode(SqlTypes.JSON)
   @Column(name = "geo_affine")
   private String geoAffine;

   @Column(name = "published_at", nullable = false)
   private Instant publishedAt = Instant.now();

   protected MapVersion() {
   }

   public MapVersion(int version, String boundary, String blocks, String passages, String entrances, String pois,
                     String streets, String labels, BigDecimal metersPerPx, String geoAffine) {
      this.version = version;
      this.boundary = boundary;
      this.blocks = blocks;
      this.passages = passages;
      this.entrances = entrances;
      this.pois = pois;
      this.streets = streets;
      this.labels = labels;
      this.metersPerPx = metersPerPx;
      this.geoAffine = geoAffine;
   }

   public Long getId() {
      return id;
   }

   public int getVersion() {
      return version;
   }

   public boolean isCurrent() {
      return current;
   }

   public void setCurrent(boolean current) {
      this.current = current;
   }

   public String getBoundary() {
      return boundary;
   }

   public String getBlocks() {
      return blocks;
   }

   public String getPassages() {
      return passages;
   }

   public String getEntrances() {
      return entrances;
   }

   public String getPois() {
      return pois;
   }

   public String getStreets() {
      return streets;
   }

   public String getLabels() {
      return labels;
   }

   public BigDecimal getMetersPerPx() {
      return metersPerPx;
   }

   public void setMetersPerPx(BigDecimal metersPerPx) {
      this.metersPerPx = metersPerPx;
   }

   public String getGeoAffine() {
      return geoAffine;
   }

   public void setGeoAffine(String geoAffine) {
      this.geoAffine = geoAffine;
   }

   public Instant getPublishedAt() {
      return publishedAt;
   }
}
