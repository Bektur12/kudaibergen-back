package kg.kudaibergen.store;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import kg.kudaibergen.common.PartCategory;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.common.web.PageResponse;
import kg.kudaibergen.store.dto.BranchRequest;
import kg.kudaibergen.store.dto.BranchResponse;
import kg.kudaibergen.store.dto.SetCategoriesRequest;
import kg.kudaibergen.store.dto.StoreDetailsResponse;
import kg.kudaibergen.store.dto.StoreSummaryResponse;
import kg.kudaibergen.store.dto.TemplateRequest;
import kg.kudaibergen.store.dto.TemplateResponse;
import kg.kudaibergen.store.dto.UpdateStoreRequest;
import kg.kudaibergen.store.entity.ReplyTemplate;
import kg.kudaibergen.store.entity.Store;
import kg.kudaibergen.store.entity.StoreBranch;
import kg.kudaibergen.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {

   private final StoreRepository stores;
   private final StoreBranchRepository branches;
   private final ReplyTemplateRepository templates;
   private final StoreMapper mapper;
   private final DealAccess dealAccess;

   public StoreService(StoreRepository stores, StoreBranchRepository branches,
                       ReplyTemplateRepository templates, StoreMapper mapper, DealAccess dealAccess) {
      this.stores = stores;
      this.branches = branches;
      this.templates = templates;
      this.mapper = mapper;
      this.dealAccess = dealAccess;
   }

   // ─────────────────────── магазин продавца ───────────────────────

   @Transactional
   public Store createForOwner(User owner, String storeName, String businessType) {
      return stores.findByOwnerId(owner.getId()).orElseGet(() -> {
         String name = storeName == null || storeName.isBlank()
               ? "Магазин " + (owner.getName() == null ? owner.getPhone() : owner.getName())
               : storeName.trim();
         return stores.save(new Store(owner, name, businessType == null ? "parts" : businessType));
      });
   }

   @Transactional(readOnly = true)
   public Store getOwnStore(Long ownerUserId) {
      return stores.findByOwnerId(ownerUserId)
            .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "У пользователя нет магазина"));
   }

   /** id магазина текущего продавца — им пользуются пакеты request/offer/analytics. */
   @Transactional(readOnly = true)
   public Long requireOwnStoreId(Long ownerUserId) {
      return getOwnStore(ownerUserId).getId();
   }

   @Transactional(readOnly = true)
   public Store getRequired(Long storeId) {
      return stores.findById(storeId)
            .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "Магазин не найден"));
   }

   @Transactional
   public StoreDetailsResponse updateOwn(Long ownerUserId, UpdateStoreRequest request) {
      Store store = getOwnStore(ownerUserId);
      if (request.name() != null && !request.name().isBlank()) {
         store.setName(request.name().trim());
      }
      if (request.description() != null) {
         store.setDescription(request.description());
      }
      if (request.businessType() != null && !request.businessType().isBlank()) {
         store.setBusinessType(request.businessType().trim());
      }
      return mapper.toDetails(store, branches.findByStoreIdOrderByIdAsc(store.getId()), true);
   }

   @Transactional(readOnly = true)
   public StoreDetailsResponse ownDetails(Long ownerUserId) {
      Store store = getOwnStore(ownerUserId);
      return mapper.toDetails(store, branches.findByStoreIdOrderByIdAsc(store.getId()), true);
   }

   // ─────────────────────── категории ───────────────────────

   @Transactional(readOnly = true)
   public Set<PartCategory> categories(Long ownerUserId) {
      return Set.copyOf(getOwnStore(ownerUserId).getCategories());
   }

   @Transactional
   public Set<PartCategory> setCategories(Long ownerUserId, SetCategoriesRequest request) {
      Store store = getOwnStore(ownerUserId);
      store.setCategories(request.categories());
      return Set.copyOf(store.getCategories());
   }

   // ─────────────────────── филиалы ───────────────────────

   @Transactional(readOnly = true)
   public List<BranchResponse> branches(Long ownerUserId) {
      Long storeId = requireOwnStoreId(ownerUserId);
      return branches.findByStoreIdOrderByIdAsc(storeId).stream()
            .map(branch -> mapper.toBranch(branch, true))
            .toList();
   }

   @Transactional
   public BranchResponse addBranch(Long ownerUserId, BranchRequest request) {
      Store store = getOwnStore(ownerUserId);
      if (request.address() == null || request.address().isBlank()) {
         throw new BadRequestException("BRANCH_ADDRESS_REQUIRED", "Укажите адрес филиала", "address");
      }
      if (request.city() == null || request.city().isBlank()) {
         throw new BadRequestException("BRANCH_CITY_REQUIRED", "Укажите город филиала", "city");
      }
      StoreBranch branch = new StoreBranch(store);
      apply(branch, request);
      return mapper.toBranch(branches.save(branch), true);
   }

   @Transactional
   public BranchResponse updateBranch(Long ownerUserId, Long branchId, BranchRequest request) {
      Long storeId = requireOwnStoreId(ownerUserId);
      StoreBranch branch = branches.findByIdAndStoreId(branchId, storeId)
            .orElseThrow(() -> new NotFoundException("BRANCH_NOT_FOUND", "Филиал не найден"));
      apply(branch, request);
      return mapper.toBranch(branch, true);
   }

   @Transactional
   public void deleteBranch(Long ownerUserId, Long branchId) {
      Long storeId = requireOwnStoreId(ownerUserId);
      StoreBranch branch = branches.findByIdAndStoreId(branchId, storeId)
            .orElseThrow(() -> new NotFoundException("BRANCH_NOT_FOUND", "Филиал не найден"));
      branches.delete(branch);
   }

   private void apply(StoreBranch branch, BranchRequest request) {
      if (request.address() != null) {
         branch.setAddress(request.address().trim());
      }
      if (request.city() != null) {
         branch.setCity(request.city().trim());
      }
      if (request.phone() != null) {
         branch.setPhone(request.phone());
      }
      if (request.latitude() != null) {
         branch.setLatitude(request.latitude());
      }
      if (request.longitude() != null) {
         branch.setLongitude(request.longitude());
      }
      if (request.workHours() != null) {
         branch.setWorkHours(mapper.writeWorkHours(request.workHours()));
      }
   }

   // ─────────────────────── шаблоны ответов ───────────────────────

   @Transactional(readOnly = true)
   public List<TemplateResponse> templates(Long ownerUserId) {
      return templates.findByStoreIdOrderBySortOrderAscIdAsc(requireOwnStoreId(ownerUserId)).stream()
            .map(TemplateResponse::of)
            .toList();
   }

   @Transactional(readOnly = true)
   public ReplyTemplate getOwnedTemplate(Long templateId, Long storeId) {
      return templates.findByIdAndStoreId(templateId, storeId)
            .orElseThrow(() -> new NotFoundException("TEMPLATE_NOT_FOUND", "Шаблон не найден"));
   }

   @Transactional
   public TemplateResponse addTemplate(Long ownerUserId, TemplateRequest request) {
      Store store = getOwnStore(ownerUserId);
      if (request.title() == null || request.title().isBlank()) {
         throw new BadRequestException("TEMPLATE_TITLE_REQUIRED", "Укажите название шаблона", "title");
      }
      if (request.body() == null || request.body().isBlank()) {
         throw new BadRequestException("TEMPLATE_BODY_REQUIRED", "Укажите текст шаблона", "body");
      }
      ReplyTemplate template = new ReplyTemplate(store);
      template.setTitle(request.title().trim());
      template.setBody(request.body().trim());
      template.setSortOrder(request.sortOrder() == null ? (short) 0 : request.sortOrder());
      return TemplateResponse.of(templates.save(template));
   }

   @Transactional
   public TemplateResponse updateTemplate(Long ownerUserId, Long templateId, TemplateRequest request) {
      ReplyTemplate template = getOwnedTemplate(templateId, requireOwnStoreId(ownerUserId));
      if (request.title() != null && !request.title().isBlank()) {
         template.setTitle(request.title().trim());
      }
      if (request.body() != null && !request.body().isBlank()) {
         template.setBody(request.body().trim());
      }
      if (request.sortOrder() != null) {
         template.setSortOrder(request.sortOrder());
      }
      return TemplateResponse.of(template);
   }

   @Transactional
   public void deleteTemplate(Long ownerUserId, Long templateId) {
      templates.delete(getOwnedTemplate(templateId, requireOwnStoreId(ownerUserId)));
   }

   // ─────────────────────── витрина для покупателя ───────────────────────

   @Transactional(readOnly = true)
   public PageResponse<StoreSummaryResponse> search(PartCategory category, String city, int page, int size) {
      var result = stores.search(category == null ? null : category.name(), city, PageRequest.of(page, size));
      List<Long> ids = result.getContent().stream().map(Store::getId).toList();
      var branchesByStore = ids.isEmpty()
            ? List.<StoreBranch>of()
            : branches.findByStoreIdInOrderByIdAsc(ids);
      return PageResponse.of(result, store -> mapper.toSummary(store, branchesByStore.stream()
            .filter(branch -> store.getId().equals(branch.getStoreId()))
            .toList()));
   }

   @Transactional(readOnly = true)
   public StoreDetailsResponse details(Long storeId, Long viewerUserId) {
      Store store = getRequired(storeId);
      boolean owner = viewerUserId != null && viewerUserId.equals(store.getOwnerUserId());
      boolean showPhones = owner || dealAccess.hasAcceptedDeal(viewerUserId, storeId);
      return mapper.toDetails(store, branches.findByStoreIdOrderByIdAsc(storeId), showPhones);
   }

   // ─────────────────────── агрегаты ───────────────────────

   @Transactional
   public void applyRating(Long storeId, BigDecimal rating, int reviewCount) {
      Store store = getRequired(storeId);
      store.setRating(rating);
      store.setReviewCount(reviewCount);
   }
}
