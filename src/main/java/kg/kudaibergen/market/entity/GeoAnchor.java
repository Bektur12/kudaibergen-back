package kg.kudaibergen.market.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Опорная точка «GPS ↔ точка схемы». */
@Entity
@Table(name = "geo_anchors")
public class GeoAnchor {

   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(nullable = false, precision = 9, scale = 6)
   private BigDecimal lat;

   @Column(nullable = false, precision = 9, scale = 6)
   private BigDecimal lon;

   @Column(nullable = false, precision = 8, scale = 2)
   private BigDecimal x;

   @Column(nullable = false, precision = 8, scale = 2)
   private BigDecimal y;

   @Column(length = 60)
   private String label;

   protected GeoAnchor() {
   }

   public GeoAnchor(BigDecimal lat, BigDecimal lon, BigDecimal x, BigDecimal y, String label) {
      this.lat = lat;
      this.lon = lon;
      this.x = x;
      this.y = y;
      this.label = label;
   }

   public Long getId() {
      return id;
   }

   public BigDecimal getLat() {
      return lat;
   }

   public BigDecimal getLon() {
      return lon;
   }

   public BigDecimal getX() {
      return x;
   }

   public BigDecimal getY() {
      return y;
   }

   public String getLabel() {
      return label;
   }
}
