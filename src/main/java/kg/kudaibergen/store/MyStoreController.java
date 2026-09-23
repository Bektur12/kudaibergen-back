package kg.kudaibergen.store;

import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.PartCategory;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.store.dto.BranchRequest;
import kg.kudaibergen.store.dto.BranchResponse;
import kg.kudaibergen.store.dto.SetCategoriesRequest;
import kg.kudaibergen.store.dto.StoreDetailsResponse;
import kg.kudaibergen.store.dto.TemplateRequest;
import kg.kudaibergen.store.dto.TemplateResponse;
import kg.kudaibergen.store.dto.UpdateStoreRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Кабинет продавца. Доступ только роли SELLER — это проверяет SecurityConfig. */
@RestController
@RequestMapping("/api/v1/my-store")
@Tag(name = "Магазин (продавец)")
public class MyStoreController {

   private final StoreService storeService;

   public MyStoreController(StoreService storeService) {
      this.storeService = storeService;
   }

   @GetMapping
   @Operation(summary = "Свой магазин")
   public StoreDetailsResponse myStore(@AuthenticationPrincipal AuthPrincipal principal) {
      return storeService.ownDetails(principal.userId());
   }

   @PatchMapping
   @Operation(summary = "Изменить название и описание")
   public StoreDetailsResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                      @Valid @RequestBody UpdateStoreRequest request) {
      return storeService.updateOwn(principal.userId(), request);
   }

   @GetMapping("/categories")
   @Operation(summary = "Категории, которыми торгует магазин")
   public Set<PartCategory> categories(@AuthenticationPrincipal AuthPrincipal principal) {
      return storeService.categories(principal.userId());
   }

   @PutMapping("/categories")
   @Operation(summary = "Заменить список категорий", description = "От них зависит, какие запросы придут магазину")
   public Set<PartCategory> setCategories(@AuthenticationPrincipal AuthPrincipal principal,
                                          @Valid @RequestBody SetCategoriesRequest request) {
      return storeService.setCategories(principal.userId(), request);
   }

   @GetMapping("/branches")
   @Operation(summary = "Филиалы магазина")
   public List<BranchResponse> branches(@AuthenticationPrincipal AuthPrincipal principal) {
      return storeService.branches(principal.userId());
   }

   @PostMapping("/branches")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Добавить филиал")
   public BranchResponse addBranch(@AuthenticationPrincipal AuthPrincipal principal,
                                   @Valid @RequestBody BranchRequest request) {
      return storeService.addBranch(principal.userId(), request);
   }

   @PatchMapping("/branches/{id}")
   @Operation(summary = "Изменить филиал")
   public BranchResponse updateBranch(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable Long id,
                                      @Valid @RequestBody BranchRequest request) {
      return storeService.updateBranch(principal.userId(), id, request);
   }

   @DeleteMapping("/branches/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить филиал")
   public void deleteBranch(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      storeService.deleteBranch(principal.userId(), id);
   }

   @GetMapping("/templates")
   @Operation(summary = "Шаблоны быстрых ответов")
   public List<TemplateResponse> templates(@AuthenticationPrincipal AuthPrincipal principal) {
      return storeService.templates(principal.userId());
   }

   @PostMapping("/templates")
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Создать шаблон")
   public TemplateResponse addTemplate(@AuthenticationPrincipal AuthPrincipal principal,
                                       @Valid @RequestBody TemplateRequest request) {
      return storeService.addTemplate(principal.userId(), request);
   }

   @PatchMapping("/templates/{id}")
   @Operation(summary = "Изменить шаблон")
   public TemplateResponse updateTemplate(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable Long id,
                                          @Valid @RequestBody TemplateRequest request) {
      return storeService.updateTemplate(principal.userId(), id, request);
   }

   @DeleteMapping("/templates/{id}")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Удалить шаблон")
   public void deleteTemplate(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      storeService.deleteTemplate(principal.userId(), id);
   }
}
