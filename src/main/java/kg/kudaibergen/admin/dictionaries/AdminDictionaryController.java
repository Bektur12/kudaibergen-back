package kg.kudaibergen.admin.dictionaries;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminBrandDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminCategoryDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminHintDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminModelDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminServiceTypeDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.AdminSynonymDto;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.BrandCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.BrandLogoRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CategoryCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateBrandRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateCategoryRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateHintRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateModelRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateServiceTypeRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.CreateSynonymRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.HintCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.ModelCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.OrderRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.ServiceOrderRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.ServiceTypeCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.SynonymCounts;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateBrandRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateCategoryRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateHintRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateModelRequest;
import kg.kudaibergen.admin.dictionaries.AdminDictionaryDtos.UpdateServiceTypeRequest;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Справочники [A5, A9]: табы «Марки и модели · Категории · Услуги · Синонимы · Подсказки». Правки сразу видны
 * в приложении: растёт GET /api/v1/dictionaries/version, меняется ETag списков. Используемое удалить нельзя —
 * 409 IN_USE, его скрывают (active = false).
 */
@RestController
@RequestMapping("/api/v1/admin/dictionaries")
@Tag(name = "Админка: справочники")
public class AdminDictionaryController {

   private final AdminDictionaryService dictionaries;

   public AdminDictionaryController(AdminDictionaryService dictionaries) {
      this.dictionaries = dictionaries;
   }

   // ─────────────── марки ───────────────

   @GetMapping("/brands")
   @PreAuthorize("hasAuthority('DICTIONARIES_VIEW')")
   @Operation(summary = "Марки со счётчиками моделей, продавцов, мастеров и машин", description = "q — название, slug, алиас")
   public AdminPage<AdminBrandDto, BrandCounts> brands(@RequestParam(required = false) String q) {
      return dictionaries.brands(q);
   }

   @GetMapping("/brands/{id}")
   @PreAuthorize("hasAuthority('DICTIONARIES_VIEW')")
   @Operation(summary = "Марка")
   public AdminBrandDto brand(@PathVariable Long id) {
      return dictionaries.brand(id);
   }

   @PostMapping("/brands")
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "BRAND_CREATE", entity = "BRAND")
   @Operation(summary = "Добавить марку")
   @ApiResponse(responseCode = "409", description = "BRAND_EXISTS")
   public AdminBrandDto createBrand(@Valid @RequestBody CreateBrandRequest request) {
      AdminBrandDto created = dictionaries.createBrand(request);
      AuditTrail.entityId(created.id());
      return created;
   }

   @PatchMapping("/brands/{id}")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "BRAND_UPDATE", entity = "BRAND", id = "#id")
   @Operation(summary = "Изменить марку", description = "null — не менять; active = false — скрыть")
   public AdminBrandDto updateBrand(@PathVariable Long id, @Valid @RequestBody UpdateBrandRequest request) {
      AuditTrail.before(dictionaries.brand(id));
      return dictionaries.updateBrand(id, request);
   }

   @PutMapping("/brands/{id}/logo")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "BRAND_LOGO_SET", entity = "BRAND", id = "#id")
   @Operation(summary = "Логотип", description = "Фото загружается через POST /media/photos с purpose=BRAND; logoUrl "
         + "станет /api/v1/brands/{id}/logo. Фото пережимается в JPEG — прозрачный фон станет белым")
   public AdminBrandDto setLogo(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal admin,
                                @Valid @RequestBody BrandLogoRequest request) {
      AuditTrail.before(dictionaries.brand(id));
      return dictionaries.setLogo(id, request.mediaId(), admin.userId());
   }

   @DeleteMapping("/brands/{id}/logo")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "BRAND_LOGO_REMOVE", entity = "BRAND", id = "#id")
   @Operation(summary = "Убрать загруженный логотип", description = "Снова буква placeholder или статичный логотип")
   public AdminBrandDto removeLogo(@PathVariable Long id) {
      AuditTrail.before(dictionaries.brand(id));
      return dictionaries.removeLogo(id);
   }

   @DeleteMapping("/brands/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "BRAND_DELETE", entity = "BRAND", id = "#id")
   @Operation(summary = "Удалить марку", description = "Только если она нигде не используется")
   @ApiResponse(responseCode = "409", description = "IN_USE — скрыть вместо удаления")
   public void deleteBrand(@PathVariable Long id) {
      AuditTrail.before(dictionaries.brand(id));
      dictionaries.deleteBrand(id);
   }

   @PutMapping("/brands/order")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "BRAND_REORDER", entity = "BRAND")
   @Operation(summary = "Порядок марок", description = "id по порядку; sortOrder станет 1, 2, 3…")
   public AdminPage<AdminBrandDto, BrandCounts> reorderBrands(@Valid @RequestBody OrderRequest request) {
      return dictionaries.reorderBrands(request.ids());
   }

   // ─────────────── модели ───────────────

   @GetMapping("/brands/{brandId}/models")
   @PreAuthorize("hasAuthority('DICTIONARIES_VIEW')")
   @Operation(summary = "Модели марки")
   public AdminPage<AdminModelDto, ModelCounts> models(@PathVariable Long brandId,
                                                       @RequestParam(required = false) String q) {
      return dictionaries.models(brandId, q);
   }

   @PostMapping("/brands/{brandId}/models")
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "MODEL_CREATE", entity = "MODEL")
   @Operation(summary = "Добавить модель")
   @ApiResponse(responseCode = "409", description = "MODEL_EXISTS")
   public AdminModelDto createModel(@PathVariable Long brandId, @Valid @RequestBody CreateModelRequest request) {
      AdminModelDto created = dictionaries.createModel(brandId, request);
      AuditTrail.entityId(created.id());
      return created;
   }

   @PatchMapping("/models/{id}")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "MODEL_UPDATE", entity = "MODEL", id = "#id")
   @Operation(summary = "Изменить модель", description = "null — не менять; пустая строка в generation / "
         + "displayName — стереть; active = false — скрыть")
   public AdminModelDto updateModel(@PathVariable Long id, @Valid @RequestBody UpdateModelRequest request) {
      AuditTrail.before(dictionaries.model(id));
      return dictionaries.updateModel(id, request);
   }

   @DeleteMapping("/models/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "MODEL_DELETE", entity = "MODEL", id = "#id")
   @Operation(summary = "Удалить модель", description = "Только если она нигде не используется")
   @ApiResponse(responseCode = "409", description = "IN_USE")
   public void deleteModel(@PathVariable Long id) {
      AuditTrail.before(dictionaries.model(id));
      dictionaries.deleteModel(id);
   }

   // ─────────────── категории ───────────────

   @GetMapping("/categories")
   @PreAuthorize("hasAuthority('DICTIONARIES_VIEW')")
   @Operation(summary = "Категории (RU / KG) со счётчиками запчастей и магазинов")
   public AdminPage<AdminCategoryDto, CategoryCounts> categories() {
      return dictionaries.categories();
   }

   @PostMapping("/categories")
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "CATEGORY_CREATE", entity = "CATEGORY")
   @Operation(summary = "Добавить категорию", description = "Встаёт в конец списка")
   @ApiResponse(responseCode = "409", description = "CATEGORY_EXISTS")
   public AdminCategoryDto createCategory(@Valid @RequestBody CreateCategoryRequest request) {
      AdminCategoryDto created = dictionaries.createCategory(request);
      AuditTrail.entityId(created.id());
      return created;
   }

   @PatchMapping("/categories/{id}")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "CATEGORY_UPDATE", entity = "CATEGORY", id = "#id")
   @Operation(summary = "Изменить категорию")
   public AdminCategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
      AuditTrail.before(dictionaries.category(id));
      return dictionaries.updateCategory(id, request);
   }

   @DeleteMapping("/categories/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "CATEGORY_DELETE", entity = "CATEGORY", id = "#id")
   @Operation(summary = "Удалить категорию")
   @ApiResponse(responseCode = "409", description = "IN_USE")
   public void deleteCategory(@PathVariable Long id) {
      AuditTrail.before(dictionaries.category(id));
      dictionaries.deleteCategory(id);
   }

   @PutMapping("/categories/order")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "CATEGORY_REORDER", entity = "CATEGORY")
   @Operation(summary = "Порядок категорий")
   public AdminPage<AdminCategoryDto, CategoryCounts> reorderCategories(@Valid @RequestBody OrderRequest request) {
      return dictionaries.reorderCategories(request.ids());
   }

   // ─────────────── услуги ───────────────

   @GetMapping("/service-types")
   @PreAuthorize("hasAuthority('DICTIONARIES_VIEW')")
   @Operation(summary = "Услуги [A9]", description = "С порядком, мастерами и заявками за 30 дней")
   public AdminPage<AdminServiceTypeDto, ServiceTypeCounts> serviceTypes() {
      return dictionaries.serviceTypes();
   }

   @PostMapping("/service-types")
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "SERVICE_TYPE_CREATE", entity = "SERVICE_TYPE", comment = "#request.code")
   @Operation(summary = "Добавить услугу", description = "Встаёт в конец списка")
   @ApiResponse(responseCode = "409", description = "SERVICE_EXISTS")
   public AdminServiceTypeDto createServiceType(@Valid @RequestBody CreateServiceTypeRequest request) {
      return dictionaries.createServiceType(request);
   }

   @PatchMapping("/service-types/{code}")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "SERVICE_TYPE_UPDATE", entity = "SERVICE_TYPE", comment = "#code")
   @Operation(summary = "Изменить услугу (правая панель)", description = "active = false — «Скрыть услугу»: её нет в "
         + "плитках и у мастеров, старые заявки показываются")
   public AdminServiceTypeDto updateServiceType(@PathVariable String code,
                                                @Valid @RequestBody UpdateServiceTypeRequest request) {
      AuditTrail.before(dictionaries.serviceType(code));
      return dictionaries.updateServiceType(code, request);
   }

   @DeleteMapping("/service-types/{code}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "SERVICE_TYPE_DELETE", entity = "SERVICE_TYPE", comment = "#code")
   @Operation(summary = "Удалить услугу")
   @ApiResponse(responseCode = "409", description = "IN_USE")
   public void deleteServiceType(@PathVariable String code) {
      AuditTrail.before(dictionaries.serviceType(code));
      dictionaries.deleteServiceType(code);
   }

   @PutMapping("/service-types/order")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "SERVICE_TYPE_REORDER", entity = "SERVICE_TYPE")
   @Operation(summary = "Порядок плиток услуг (drag-and-drop)", description = "Все коды по порядку")
   public AdminPage<AdminServiceTypeDto, ServiceTypeCounts> reorderServiceTypes(
         @Valid @RequestBody ServiceOrderRequest request) {
      return dictionaries.reorderServiceTypes(request.codes());
   }

   // ─────────────── синонимы ───────────────

   @GetMapping("/synonyms")
   @PreAuthorize("hasAuthority('DICTIONARIES_VIEW')")
   @Operation(summary = "Синонимы поиска запчастей")
   public AdminPage<AdminSynonymDto, SynonymCounts> synonyms(@RequestParam(required = false) String q) {
      return dictionaries.synonyms(q);
   }

   @PostMapping("/synonyms")
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "SYNONYM_CREATE", entity = "SYNONYM")
   @Operation(summary = "Добавить синоним", description = "bidirectional (по умолчанию) — и обратная пара")
   public List<AdminSynonymDto> createSynonym(@Valid @RequestBody CreateSynonymRequest request) {
      return dictionaries.createSynonym(request);
   }

   @DeleteMapping("/synonyms/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "SYNONYM_DELETE", entity = "SYNONYM", id = "#id")
   @Operation(summary = "Удалить пару синонимов", description = "Обратная пара — отдельной строкой")
   public void deleteSynonym(@PathVariable Long id) {
      AuditTrail.before(dictionaries.deleteSynonym(id));
   }

   // ─────────────── подсказки ───────────────

   @GetMapping("/hints")
   @PreAuthorize("hasAuthority('DICTIONARIES_VIEW')")
   @Operation(summary = "Подсказки «Что ищем?» (06)")
   public AdminPage<AdminHintDto, HintCounts> hints() {
      return dictionaries.hints();
   }

   @PostMapping("/hints")
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "HINT_CREATE", entity = "HINT")
   @Operation(summary = "Добавить подсказку")
   public AdminHintDto createHint(@Valid @RequestBody CreateHintRequest request) {
      AdminHintDto created = dictionaries.createHint(request);
      AuditTrail.entityId(created.id());
      return created;
   }

   @PatchMapping("/hints/{id}")
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "HINT_UPDATE", entity = "HINT", id = "#id")
   @Operation(summary = "Изменить подсказку", description = "categoryId = 0 — убрать категорию")
   public AdminHintDto updateHint(@PathVariable Long id, @Valid @RequestBody UpdateHintRequest request) {
      AuditTrail.before(dictionaries.hint(id));
      return dictionaries.updateHint(id, request);
   }

   @DeleteMapping("/hints/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "HINT_DELETE", entity = "HINT", id = "#id")
   @Operation(summary = "Удалить подсказку")
   @ApiResponse(responseCode = "409", description = "IN_USE")
   public void deleteHint(@PathVariable Long id) {
      AuditTrail.before(dictionaries.hint(id));
      dictionaries.deleteHint(id);
   }
}
