package kg.kudaibergen.admin.market;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.market.AdminMapDtos.AdminMapDto;
import kg.kudaibergen.admin.market.AdminMapDtos.AdminRowDetailDto;
import kg.kudaibergen.admin.market.AdminMapDtos.PublishMapRequest;
import kg.kudaibergen.admin.market.AdminMapDtos.AdminRowDto;
import kg.kudaibergen.admin.market.AdminMapDtos.GeoAnchorsDto;
import kg.kudaibergen.admin.market.AdminMapDtos.RowCounts;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.market.admin.AdminMarketDtos.AdminContainerDto;
import kg.kudaibergen.market.admin.AdminMarketDtos.CreateContainerRequest;
import kg.kudaibergen.market.admin.AdminMarketDtos.MapUploadRequest;
import kg.kudaibergen.market.admin.AdminMarketDtos.PublishedMapDto;
import kg.kudaibergen.market.admin.MarketAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Рынок и карта [A3]: черновик схемы и публикация, ряды и сетка мест, создание и удаление контейнеров,
 * опорные GPS-точки. Правка контейнера, число мест в ряду, QR и прямая публикация — в MarketAdminController.
 */
@RestController
@RequestMapping("/api/v1/admin/market")
@Tag(name = "Админка: карта")
public class AdminMapController {

   private final AdminMapService maps;
   private final MarketAdminService market;

   public AdminMapController(AdminMapService maps, MarketAdminService market) {
      this.maps = maps;
      this.market = market;
   }

   @GetMapping("/map")
   @PreAuthorize("hasAuthority('MARKET_VIEW')")
   @Operation(summary = "Схема: опубликованная версия и черновик", description = "current — что видят приложения "
         + "(«версия 7 · опубликована 21.09»); published — она же в формате загрузки; draft — если есть, с проверкой")
   public AdminMapDto map() {
      return maps.map();
   }

   @PutMapping("/map/draft")
   @PreAuthorize("hasAuthority('MARKET_EDIT')")
   @Audited(action = "MAP_DRAFT_SAVE", entity = "MAP")
   @Operation(summary = "Сохранить черновик схемы", description = "Формат — как PUT /admin/market/map. Приложения "
         + "черновик не видят. Ошибка схемы не мешает сохранить — она в draft.error")
   public AdminMapDto saveDraft(@Valid @RequestBody MapUploadRequest request,
                                @AuthenticationPrincipal AuthPrincipal admin) {
      AdminMapDto saved = maps.saveDraft(request, admin.userId());
      AuditTrail.after(saved.draft() == null ? null : new DraftSummary(saved.draft().basedOnVersion(),
            saved.draft().valid(), saved.draft().error(), request.rows().size()));
      return saved;
   }

   @DeleteMapping("/map/draft")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('MARKET_EDIT')")
   @Audited(action = "MAP_DRAFT_DISCARD", entity = "MAP")
   @Operation(summary = "Удалить черновик")
   public void discardDraft() {
      maps.discardDraft();
   }

   @PostMapping("/map/publish")
   @PreAuthorize("hasAuthority('MARKET_MAP_PUBLISH')")
   @Audited(action = "MAP_PUBLISH", entity = "MAP", comment = "#request?.comment")
   @Operation(summary = "Опубликовать черновик", description = "Новая версия схемы, приложения перекачивают карту. "
         + "Ряды сопоставляются по коду: контейнеры и магазины остаются на своих рядах, отсутствующие ряды выключаются. "
         + "Тело необязательно: {comment} — что поменялось, для журнала")
   @ApiResponse(responseCode = "400", description = "BAD_MAP — схема не проходит проверку")
   @ApiResponse(responseCode = "409", description = "NO_DRAFT")
   public PublishedMapDto publish(@AuthenticationPrincipal AuthPrincipal admin,
                                  @Valid @RequestBody(required = false) PublishMapRequest request) {
      return maps.publish(admin.userId());
   }

   @GetMapping("/rows")
   @PreAuthorize("hasAuthority('MARKET_VIEW')")
   @Operation(summary = "Ряды со счётчиками", description = "«Контейнеров 16 · С продавцом 13 · Свободно 3 · "
         + "Северная 8 · Южная 8». includeInactive — с выключенными рядами")
   public AdminPage<AdminRowDto, RowCounts> rows(@RequestParam(defaultValue = "false") boolean includeInactive) {
      return maps.rows(includeInactive);
   }

   @GetMapping("/rows/{id}")
   @PreAuthorize("hasAuthority('MARKET_VIEW')")
   @Operation(summary = "Ряд: счётчики и сетка мест по сторонам", description = "В клетке — арендатор, кто стоит "
         + "и кто переезжает сюда")
   public AdminRowDetailDto row(@PathVariable Long id) {
      return maps.row(id);
   }

   @PostMapping("/containers")
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('MARKET_EDIT')")
   @Audited(action = "CONTAINER_CREATE", entity = "CONTAINER")
   @Operation(summary = "Добавить контейнер", description = "Сторона — из сторон ряда; номер на стороне уникален")
   @ApiResponse(responseCode = "409", description = "CONTAINER_EXISTS")
   public AdminContainerDto createContainer(@Valid @RequestBody CreateContainerRequest request) {
      AdminContainerDto created = market.createContainer(request);
      AuditTrail.entityId(created.id());
      return created;
   }

   @DeleteMapping("/containers/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('MARKET_EDIT')")
   @Audited(action = "CONTAINER_DELETE", entity = "CONTAINER", id = "#id")
   @Operation(summary = "Удалить контейнер", description = "Только пустой и без истории")
   @ApiResponse(responseCode = "409", description = "CONTAINER_OCCUPIED — стоит магазин; CONTAINER_IN_USE — есть "
         + "история, выключите (PATCH active = false)")
   public void deleteContainer(@PathVariable Long id) {
      market.deleteContainer(id);
   }

   @GetMapping("/geo-anchors")
   @PreAuthorize("hasAuthority('MARKET_VIEW')")
   @Operation(summary = "Опорные GPS-точки", description = "Заменить набор — PUT /admin/market/geo-anchors")
   public GeoAnchorsDto geoAnchors() {
      return maps.geoAnchors();
   }

   /** В журнал — итог, а не вся схема. */
   record DraftSummary(int basedOnVersion, boolean valid, String error, int rows) {
   }
}
