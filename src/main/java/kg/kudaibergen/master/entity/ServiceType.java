package kg.kudaibergen.master.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kg.kudaibergen.user.entity.Lang;

/**
 * Услуга из справочника (плитки 33, 38). needsLocation — нужна точка на карте (эвакуатор, выездной мастер);
 * durationMin — сколько по умолчанию ждать отклики; urgent — пометка «Срочно».
 */
@Entity
@Table(name = "service_types")
public class ServiceType {

   @Id
   @Column(length = 20)
   private String code;

   @Column(name = "name_ru", nullable = false, length = 40)
   private String nameRu;

   @Column(name = "name_kg", nullable = false, length = 40)
   private String nameKg;

   @Column(nullable = false, length = 30)
   private String icon;

   @Column(name = "sort_order", nullable = false)
   private short sortOrder;

   @Column(name = "needs_location", nullable = false)
   private boolean needsLocation;

   @Column(name = "duration_min", nullable = false)
   private short durationMin;

   @Column(nullable = false)
   private boolean urgent;

   protected ServiceType() {
   }

   public String name(Lang lang) {
      return lang == Lang.KG ? nameKg : nameRu;
   }

   public String getCode() {
      return code;
   }

   public String getIcon() {
      return icon;
   }

   public short getSortOrder() {
      return sortOrder;
   }

   public boolean isNeedsLocation() {
      return needsLocation;
   }

   public short getDurationMin() {
      return durationMin;
   }

   public boolean isUrgent() {
      return urgent;
   }
}
