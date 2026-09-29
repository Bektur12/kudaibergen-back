package kg.kudaibergen.catalog;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import kg.kudaibergen.catalog.dto.CarFilter;
import kg.kudaibergen.catalog.dto.MyPartItemDto;
import kg.kudaibergen.catalog.dto.MyPartsSummaryDto;
import kg.kudaibergen.catalog.dto.PartCardDto;
import kg.kudaibergen.catalog.dto.PartDetailDto;
import kg.kudaibergen.catalog.dto.PartInput;
import kg.kudaibergen.catalog.dto.PartSort;
import kg.kudaibergen.catalog.entity.Fitment;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.catalog.entity.PartStatus;
import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.ConflictException;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.media.Media;
import kg.kudaibergen.media.MediaPurpose;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.entity.ShopMember;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Каталог продавца (ТЗ 9): «Мои запчасти» (24), форма (26), публикация, наличие, архив, удаление.
 * Выкладывают и правят владелец и сотрудники, удаляет только владелец (ТЗ 2). Черновик сохраняется
 * неполным; опубликованный товар всегда полный — правка не может его «сломать».
 */
@Service
public class MyPartsService {

   /** Активных товаров на магазин (ТЗ 9.3). */
   static final int MAX_ACTIVE = 500;
   static final int MAX_PHOTOS = 6;
   static final int MAX_FITMENTS = 20;
   private static final int VIEWS_WEEK_DAYS = 7;

   /** Какую часть списка показать (24). ALL — всё, кроме архива. */
   public enum PartsFilter {
      ALL,
      IN_STOCK,
      OUT_OF_STOCK,
      DRAFT,
      ARCHIVED
   }

   private final PartRepository parts;
   private final CatalogView view;
   private final PartSearch search;
   private final ShopAccess access;
   private final ShopMemberRepository members;
   private final MediaService media;
   private final CategoryService categories;
   private final VehicleDirectory directory;
   private final ApplicationEventPublisher events;
   private final Clock clock;

   public MyPartsService(PartRepository parts, CatalogView view, PartSearch search, ShopAccess access,
                         ShopMemberRepository members, MediaService media, CategoryService categories,
                         VehicleDirectory directory, ApplicationEventPublisher events, Clock clock) {
      this.parts = parts;
      this.view = view;
      this.search = search;
      this.access = access;
      this.members = members;
      this.media = media;
      this.categories = categories;
      this.directory = directory;
      this.events = events;
      this.clock = clock;
   }

   // ─────────────────────── списки ───────────────────────

   @Transactional(readOnly = true)
   public CursorPage<MyPartItemDto> list(Long userId, PartsFilter filter, String query, String cursor, Integer limit,
                                         Lang lang) {
      Shop shop = access.requireMember(userId).shop();
      int size = CursorPage.limit(limit);
      long beforeId = cursor == null || cursor.isBlank() ? Long.MAX_VALUE : CursorPage.afterId(cursor);
      PartStatus status = switch (filter) {
         case ALL -> null;
         case IN_STOCK, OUT_OF_STOCK -> PartStatus.ACTIVE;
         case DRAFT -> PartStatus.DRAFT;
         case ARCHIVED -> PartStatus.ARCHIVED;
      };
      Boolean stock = filter == PartsFilter.IN_STOCK ? Boolean.TRUE : filter == PartsFilter.OUT_OF_STOCK ? Boolean.FALSE : null;
      String like = query == null || query.isBlank() ? null
            : "%" + query.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
      String oemNorm = like == null ? null : Part.normalizeOem(query);
      // пустой шаблон ничего не находит: номер детали не ищем, если в строке нет букв и цифр
      String oem = oemNorm == null ? "" : "%" + oemNorm + "%";
      List<Part> rows = parts.findMine(shop.getId(), beforeId, status, PartStatus.ARCHIVED, stock, like, oem,
            PageRequest.of(0, size + 1));
      boolean more = rows.size() > size;
      List<Part> page = more ? rows.subList(0, size) : rows;
      return new CursorPage<>(view.myItems(page, lang),
            more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1).getId())) : null);
   }

   @Transactional(readOnly = true)
   public MyPartsSummaryDto summary(Long userId) {
      Long shopId = access.requireMember(userId).shop().getId();
      long active = parts.countByShopIdAndStatus(shopId, PartStatus.ACTIVE);
      long drafts = parts.countByShopIdAndStatus(shopId, PartStatus.DRAFT);
      return new MyPartsSummaryDto(active + drafts, parts.countInStock(shopId, PartStatus.ACTIVE),
            parts.countOutOfStock(shopId, PartStatus.ACTIVE), drafts,
            parts.countByShopIdAndStatus(shopId, PartStatus.ARCHIVED), parts.viewsSince(shopId, VIEWS_WEEK_DAYS),
            MAX_ACTIVE);
   }

   @Transactional(readOnly = true)
   public PartDetailDto get(Long userId, Long partId, Lang lang) {
      return view.detail(own(userId, partId, false), null, null, lang);
   }

   // ─────────────────────── форма ───────────────────────

   /** «Выложить запчасть» (26): черновик сразу; publish = true — сразу в поиск, если всё заполнено. */
   @Transactional
   public PartDetailDto create(Long userId, PartInput input, boolean publish, Lang lang) {
      Shop shop = access.requireMember(userId).shop();
      Part part = new Part(shop.getId());
      apply(part, input);
      parts.saveAndFlush(part);
      if (publish) {
         publish(part);
      }
      return view.detail(part, null, null, lang);
   }

   /** Черновик из импорта Excel (без фото). Вызывается внутри транзакции импорта, доступ уже проверен. */
   @Transactional(propagation = Propagation.MANDATORY)
   public Long createImported(Long shopId, PartInput input) {
      Part part = new Part(shopId);
      apply(part, input);
      return parts.save(part).getId();
   }

   /** Сохранение на ходу и правка: null — поле не меняется, пустой список — очистить. */
   @Transactional
   public PartDetailDto update(Long userId, Long partId, PartInput input, Lang lang) {
      Part part = own(userId, partId, true);
      Integer oldPrice = part.getPrice();
      boolean wasInStock = part.inStock();
      apply(part, input);
      if (part.isActive()) {
         requireComplete(part);
         // пуши тем, у кого в избранном (ТЗ 5.5) — после коммита
         if (oldPrice != null && part.getPrice() < oldPrice) {
            events.publishEvent(new PartEvents.PriceDropped(part.getId(), oldPrice, part.getPrice()));
         }
         if (wasInStock && !part.inStock()) {
            events.publishEvent(new PartEvents.OutOfStock(part.getId()));
         }
      }
      return view.detail(part, null, null, lang);
   }

   @Transactional
   public PartDetailDto publish(Long userId, Long partId, Lang lang) {
      Part part = own(userId, partId, true);
      publish(part);
      return view.detail(part, null, null, lang);
   }

   /** «В архив» — пропадает из поиска и из списка «Все», остаётся в «Архив». */
   @Transactional
   public PartDetailDto archive(Long userId, Long partId, Lang lang) {
      Part part = own(userId, partId, true);
      part.setStatus(PartStatus.ARCHIVED);
      return view.detail(part, null, null, lang);
   }

   /** Вернуть из архива: полный товар — снова в поиск, неполный — в черновики. */
   @Transactional
   public PartDetailDto restore(Long userId, Long partId, Lang lang) {
      Part part = own(userId, partId, true);
      if (part.getStatus() != PartStatus.ARCHIVED) {
         throw new ConflictException("NOT_ARCHIVED", "Запчасть не в архиве");
      }
      if (missing(part).isEmpty()) {
         publish(part);
      } else {
         part.setStatus(PartStatus.DRAFT);
      }
      return view.detail(part, null, null, lang);
   }

   /** Удалить может только владелец бокса (ТЗ 2). */
   @Transactional
   public void delete(Long userId, Long partId) {
      Shop shop = access.requireOwner(userId).shop();
      Part part = parts.findById(partId).filter(found -> found.getShopId().equals(shop.getId()))
            .orElseThrow(CatalogService::notFound);
      parts.delete(part);
   }

   /** Модерация: админ рынка удаляет запчасть по жалобе (ТЗ 13.1). */
   @Transactional
   public void adminDelete(Long partId) {
      parts.delete(parts.findById(partId).orElseThrow(CatalogService::notFound));
   }

   // ─────────────────────── подсказка к ответу «Есть» ───────────────────────

   /**
    * «Приложить товар из каталога» (12): свои опубликованные запчасти, подходящие машине запроса,
    * сначала совпавшие с текстом. Ничего по тексту — просто подходящие к машине.
    */
   @Transactional(readOnly = true)
   public List<PartCardDto> suggestions(Long shopId, CarFilter car, String text) {
      int limit = 10;
      PartSearch.Query byText = new PartSearch.Query(text, car, null, null, null, null, false, false, shopId,
            PartSort.NEWEST, List.of());
      LinkedHashSet<Long> ids = new LinkedHashSet<>();
      search.find(byText, 0, limit).forEach(hit -> ids.add(hit.partId()));
      if (ids.size() < limit) {
         PartSearch.Query byCar = new PartSearch.Query(null, car, null, null, null, null, false, false, shopId,
               PartSort.NEWEST, List.of());
         search.find(byCar, 0, limit).forEach(hit -> ids.add(hit.partId()));
      }
      return view.cards(ids.stream().limit(limit).toList(), car, null);
   }

   /** Опубликованная запчасть этого бокса — для ответа «Есть» с товаром. */
   @Transactional(readOnly = true)
   public Part requireActiveOf(Long shopId, Long partId) {
      return parts.findById(partId)
            .filter(part -> part.getShopId().equals(shopId) && part.isActive())
            .orElseThrow(() -> new BadRequestException("PART_NOT_AVAILABLE",
                  "Можно приложить только свою опубликованную запчасть"));
   }

   // ─────────────────────── правила ───────────────────────

   private Part own(Long userId, Long partId, boolean lock) {
      Shop shop = access.requireMember(userId).shop();
      return (lock ? parts.findForUpdate(partId) : parts.findById(partId))
            .filter(part -> part.getShopId().equals(shop.getId()))
            .orElseThrow(CatalogService::notFound);
   }

   private void apply(Part part, PartInput input) {
      if (input.title() != null) {
         part.setTitle(blankToNull(input.title()));
      }
      if (input.categoryId() != null) {
         categories.requireExisting(Set.of(input.categoryId()));
         part.setCategoryId(input.categoryId());
      }
      if (input.condition() != null) {
         part.setCondition(input.condition());
      }
      if (input.price() != null) {
         part.setPrice(input.price());
      }
      if (input.quantity() != null) {
         part.setQuantity(input.quantity());
      }
      if (input.manufacturer() != null) {
         part.setManufacturer(blankToNull(input.manufacturer()));
      }
      if (input.oemNumber() != null) {
         part.setOemNumber(blankToNull(input.oemNumber()));
      }
      if (input.side() != null) {
         part.setSide(input.side());
      }
      if (input.position() != null) {
         part.setPosition(input.position());
      }
      if (input.mediaIds() != null) {
         part.replacePhotos(photos(part, input.mediaIds()));
      }
      if (input.fitments() != null) {
         part.replaceFitments(fitments(input.fitments()));
      }
   }

   /**
    * Фото товара: загружены как PART кем-то из людей этого бокса (или уже стоят на товаре),
    * без повторов, не больше 6. Порядок — как прислали, первое — главное.
    */
   private List<Long> photos(Part part, List<Long> mediaIds) {
      List<Long> ids = new ArrayList<>(new LinkedHashSet<>(mediaIds));
      if (ids.size() > MAX_PHOTOS) {
         throw new BadRequestException("TOO_MANY_PHOTOS", "Не больше " + MAX_PHOTOS + " фото");
      }
      Set<Long> staff = new HashSet<>(members.findByShopIdOrderByCreatedAtAsc(part.getShopId()).stream()
            .map(ShopMember::getUserId).toList());
      Set<Long> current = new HashSet<>(part.getPhotoIds());
      List<Media> found = media.findAll(ids);
      Set<Long> allowed = new HashSet<>();
      for (Media item : found) {
         if (current.contains(item.getId())
               || item.getPurpose() == MediaPurpose.PART && staff.contains(item.getOwnerId())) {
            allowed.add(item.getId());
         }
      }
      if (!allowed.containsAll(ids)) {
         throw new BadRequestException("BAD_PHOTO", "Фото не найдено — загрузите его заново");
      }
      return ids;
   }

   private List<Fitment> fitments(List<PartInput.FitmentInput> inputs) {
      if (inputs.size() > MAX_FITMENTS) {
         throw new BadRequestException("TOO_MANY_FITMENTS", "Не больше " + MAX_FITMENTS + " машин");
      }
      List<Fitment> result = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (PartInput.FitmentInput input : inputs) {
         directory.brand(input.brandId());
         if (input.modelId() != null) {
            CarModel model = directory.model(input.modelId());
            if (!model.getBrandId().equals(input.brandId())) {
               throw new BadRequestException("MODEL_MISMATCH", "Модель не относится к выбранной марке");
            }
         }
         if (input.yearFrom() != null && input.yearTo() != null && input.yearFrom() > input.yearTo()) {
            throw new BadRequestException("BAD_YEARS", "Год «от» больше года «до»");
         }
         String key = input.brandId() + ":" + input.modelId() + ":" + input.yearFrom() + ":" + input.yearTo();
         if (seen.add(key)) {
            result.add(new Fitment(input.brandId(), input.modelId(), input.yearFrom(), input.yearTo()));
         }
      }
      return result;
   }

   private void publish(Part part) {
      requireComplete(part);
      if (!part.isActive() && parts.countByShopIdAndStatus(part.getShopId(), PartStatus.ACTIVE) >= MAX_ACTIVE) {
         throw new ConflictException("ACTIVE_PARTS_LIMIT",
               "Не больше " + MAX_ACTIVE + " запчастей в продаже — уберите ненужные в архив");
      }
      part.publish(clock.instant());
   }

   /** Без обязательных полей, фото и машин товар не публикуется (ТЗ 9.3). */
   private void requireComplete(Part part) {
      List<String> missing = missing(part);
      if (!missing.isEmpty()) {
         throw (BadRequestException) new BadRequestException("PART_INCOMPLETE",
               "Заполните обязательные поля: " + String.join(", ", missing)).with("missing", missing);
      }
   }

   /** Незаполненные обязательные поля — имена полей формы. */
   static List<String> missing(Part part) {
      List<String> missing = new ArrayList<>();
      if (part.getTitle() == null || part.getTitle().length() < 3) {
         missing.add("title");
      }
      if (part.getCategoryId() == null) {
         missing.add("categoryId");
      }
      if (part.getCondition() == null) {
         missing.add("condition");
      }
      if (part.getPrice() == null) {
         missing.add("price");
      }
      if (part.getPhotoIds().isEmpty()) {
         missing.add("mediaIds");
      }
      if (part.getFitments().isEmpty()) {
         missing.add("fitments");
      }
      return missing;
   }

   private static String blankToNull(String text) {
      return text == null || text.isBlank() ? null : text.trim();
   }
}
