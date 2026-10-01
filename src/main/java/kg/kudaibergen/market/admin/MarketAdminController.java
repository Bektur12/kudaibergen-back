package kg.kudaibergen.market.admin;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.audit.Audited;
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

/** Админка карты: контейнеры — MARKET_EDIT, схема и GPS-точки — MARKET_MAP_PUBLISH, QR — MARKET_VIEW. */
@RestController
@RequestMapping("/api/v1/admin/market")
@Tag(name = "Админка: карта")
public class MarketAdminController {

   private final MarketAdminService admin;

   public MarketAdminController(MarketAdminService admin) {
      this.admin = admin;
   }

   @PutMapping("/rows/{id}/containers")
   @PreAuthorize("hasAuthority('MARKET_EDIT')")
   @Audited(action = "ROW_CONTAINERS_SET", entity = "ROW", id = "#id")
   @Operation(summary = "Число мест на сторонах ряда",
         description = "Недостающие места создаются, лишние выключаются (если там нет магазина)")
   public List<AdminContainerDto> setCounts(@PathVariable Long id, @Valid @RequestBody ContainerCountsRequest request) {
      return admin.setContainerCounts(id, request.counts());
   }

   @PatchMapping("/containers/{id}")
   @PreAuthorize("hasAuthority('MARKET_EDIT')")
   @Audited(action = "CONTAINER_UPDATE", entity = "CONTAINER", id = "#id")
   @Operation(summary = "Изменить контейнер", description = "Арендатор (tenantName, tenantPhone; пустая строка — "
         + "убрать), перенумерация (side, number, posInRow), active = false — выключить место")
   public AdminContainerDto updateContainer(@PathVariable Long id,
                                            @Valid @RequestBody UpdateContainerRequest request) {
      return admin.updateContainer(id, request);
   }

   @GetMapping(value = "/qr/{token}.png", produces = MediaType.IMAGE_PNG_VALUE)
   @PreAuthorize("hasAuthority('MARKET_VIEW')")
   @Operation(summary = "QR-код ряда или контейнера (PNG)")
   public ResponseEntity<byte[]> qr(@PathVariable String token) {
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(admin.qrPng(token));
   }

   @GetMapping(value = "/qr/sheet", produces = MediaType.TEXT_HTML_VALUE)
   @PreAuthorize("hasAuthority('MARKET_VIEW')")
   @Operation(summary = "Лист QR-наклеек ряда для печати", description = "Табличка ряда + наклейки всех контейнеров")
   public String qrSheet(@RequestParam Long rowId) {
      return admin.qrSheet(rowId);
   }

   @PutMapping("/geo-anchors")
   @PreAuthorize("hasAuthority('MARKET_MAP_PUBLISH')")
   @Audited(action = "GEO_ANCHORS_SET", entity = "MAP")
   @Operation(summary = "Опорные GPS-точки",
         description = "3–4 угла рынка: GPS ↔ точка схемы. Пересчитывает матрицу; невязка > 10 м — точку сняли неточно")
   public CalibrationDto geoAnchors(@Valid @RequestBody GeoAnchorsRequest request) {
      return admin.setGeoAnchors(request);
   }

   @PutMapping("/map")
   @PreAuthorize("hasAuthority('MARKET_MAP_PUBLISH')")
   @Audited(action = "MAP_PUBLISH", entity = "MAP")
   @Operation(summary = "Опубликовать схему сразу, без черновика",
         description = "Формат market-map.json + ряды с кодами. Ряды сопоставляются по коду; приложения скачают новую версию")
   public PublishedMapDto publish(@Valid @RequestBody MapUploadRequest request) {
      return admin.publishMap(request);
   }
}
