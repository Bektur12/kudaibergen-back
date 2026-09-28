package kg.kudaibergen.market.admin;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.market.admin.AdminMarketDtos.AdminContainerDto;
import kg.kudaibergen.market.admin.AdminMarketDtos.CalibrationDto;
import kg.kudaibergen.market.admin.AdminMarketDtos.ContainerCountsRequest;
import kg.kudaibergen.market.admin.AdminMarketDtos.GeoAnchorsRequest;
import kg.kudaibergen.market.admin.AdminMarketDtos.MapUploadRequest;
import kg.kudaibergen.market.admin.AdminMarketDtos.PublishedMapDto;
import kg.kudaibergen.market.admin.AdminMarketDtos.UpdateContainerRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Админка карты. /api/v1/admin/** пускает админов рынка и суперадминов (SecurityConfig);
 * схема и GPS-точки — только суперадмин.
 */
@RestController
@RequestMapping("/api/v1/admin/market")
@Tag(name = "Админка: карта")
public class MarketAdminController {

   private final MarketAdminService admin;

   public MarketAdminController(MarketAdminService admin) {
      this.admin = admin;
   }

   @PutMapping("/rows/{id}/containers")
   @Operation(summary = "Число мест на сторонах ряда",
         description = "Недостающие места создаются, лишние выключаются (если там нет магазина)")
   public List<AdminContainerDto> setCounts(@PathVariable Long id, @Valid @RequestBody ContainerCountsRequest request) {
      return admin.setContainerCounts(id, request.counts());
   }

   @PatchMapping("/containers/{id}")
   @Operation(summary = "Номер арендатора и активность контейнера")
   public AdminContainerDto updateContainer(@PathVariable Long id,
                                            @Valid @RequestBody UpdateContainerRequest request) {
      return admin.updateContainer(id, request);
   }

   @GetMapping(value = "/qr/{token}.png", produces = MediaType.IMAGE_PNG_VALUE)
   @Operation(summary = "QR-код ряда или контейнера (PNG)")
   public ResponseEntity<byte[]> qr(@PathVariable String token) {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(admin.qrPng(token));
   }

   @GetMapping(value = "/qr/sheet", produces = MediaType.TEXT_HTML_VALUE)
   @Operation(summary = "Лист QR-наклеек ряда для печати", description = "Табличка ряда + наклейки всех контейнеров")
   public String qrSheet(@RequestParam Long rowId) {
      return admin.qrSheet(rowId);
   }

   @PutMapping("/geo-anchors")
   @PreAuthorize("hasRole('SUPERADMIN')")
   @Operation(summary = "Опорные GPS-точки (суперадмин)",
         description = "3–4 угла рынка: GPS ↔ точка схемы. Пересчитывает матрицу; невязка > 10 м — точку сняли неточно")
   public CalibrationDto geoAnchors(@Valid @RequestBody GeoAnchorsRequest request) {
      return admin.setGeoAnchors(request);
   }

   @PutMapping("/map")
   @PreAuthorize("hasRole('SUPERADMIN')")
   @Operation(summary = "Опубликовать новую версию схемы (суперадмин)",
         description = "Формат market-map.json + ряды с кодами. Ряды сопоставляются по коду; приложения скачают новую версию")
   public PublishedMapDto publish(@Valid @RequestBody MapUploadRequest request) {
      return admin.publishMap(request);
   }
}
