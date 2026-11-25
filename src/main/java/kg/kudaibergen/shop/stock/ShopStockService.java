package kg.kudaibergen.shop.stock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.common.error.ApiException;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.request.PartHintRepository;
import kg.kudaibergen.request.entity.PartHint;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.shop.stock.dto.StockDtos.AddStockItem;
import kg.kudaibergen.shop.stock.dto.StockDtos.StockItemDto;
import kg.kudaibergen.shop.stock.dto.StockDtos.StockItemInput;
import kg.kudaibergen.shop.stock.dto.StockDtos.StockSuggestionDto;
import kg.kudaibergen.shop.stock.dto.StockDtos.StockingShopsDto;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static kg.kudaibergen.shop.stock.dto.StockDtos.MAX_ITEMS;

/**
 * «Что у меня всегда есть» (регистрация продавца и профиль) и «Обычно есть у…» для покупателя.
 * Деталь — из справочника подсказок, как в запросе: «колодки на Camry» находит ровно те магазины, что отметили
 * колодки на Toyota Camry, на Toyota или на все свои марки (если среди них Toyota).
 */
@Service
public class ShopStockService {

   static final int DEFAULT_LIMIT = 20;
   static final int MAX_LIMIT = 50;

   private final ShopStockRepository stock;
   private final PartHintRepository hints;
   private final VehicleDirectory directory;
   private final ShopAccess access;
   private final ShopRepository shops;
   private final ShopMapper mapper;

   public ShopStockService(ShopStockRepository stock, PartHintRepository hints, VehicleDirectory directory,
                           ShopAccess access, ShopRepository shops, ShopMapper mapper) {
      this.stock = stock;
      this.hints = hints;
      this.directory = directory;
      this.access = access;
      this.shops = shops;
      this.mapper = mapper;
   }

   // ─────────────────────── продавец ───────────────────────

   @Transactional(readOnly = true)
   public List<StockItemDto> mine(Long userId, Lang lang) {
      Shop shop = access.requireMember(userId).shop();
      return toDtos(stock.findByShopIdOrderByIdAsc(shop.getId()), lang);
   }

   /** Весь список целиком (экран «Что у меня всегда есть»). Строки, принятые из подсказок, помнят источник. */
   @Transactional
   public List<StockItemDto> replace(Long userId, List<StockItemInput> items, Lang lang) {
      Shop shop = access.requireOwner(userId).shop();
      List<StockKey> keys = normalize(items, shop.getBrandIds());
      Map<StockKey, StockSource> sources = stock.findByShopIdOrderByIdAsc(shop.getId()).stream()
            .collect(Collectors.toMap(ShopStockItem::key, ShopStockItem::getSource, (a, b) -> a));
      stock.deleteAllOfShop(shop.getId());
      stock.saveAll(keys.stream()
            .map(key -> new ShopStockItem(shop.getId(), key, sources.getOrDefault(key, StockSource.SELF)))
            .toList());
      return toDtos(stock.findByShopIdOrderByIdAsc(shop.getId()), lang);
   }

   /** Одна строка — «Добавить» у подсказки из ответов. Уже есть — ничего не меняется. */
   @Transactional
   public List<StockItemDto> add(Long userId, AddStockItem item, Lang lang) {
      Shop shop = access.requireOwner(userId).shop();
      StockKey key = normalize(List.of(new StockItemInput(item.hintId(), item.brandId(), item.modelId())),
            shop.getBrandIds()).getFirst();
      List<ShopStockItem> current = stock.findByShopIdOrderByIdAsc(shop.getId());
      if (current.stream().noneMatch(row -> row.key().equals(key))) {
         if (current.size() >= MAX_ITEMS) {
            throw new BadRequestException("STOCK_FULL", "В списке уже " + MAX_ITEMS + " позиций — уберите лишнее");
         }
         stock.save(new ShopStockItem(shop.getId(), key, item.fromAnswers() ? StockSource.ANSWERS : StockSource.SELF));
      }
      return toDtos(stock.findByShopIdOrderByIdAsc(shop.getId()), lang);
   }

   @Transactional
   public List<StockItemDto> remove(Long userId, Long itemId, Lang lang) {
      Shop shop = access.requireOwner(userId).shop();
      ShopStockItem row = stock.findById(itemId)
            .filter(found -> found.getShopId().equals(shop.getId()))
            .orElseThrow(() -> new NotFoundException("STOCK_ITEM_NOT_FOUND", "Позиция не найдена"));
      stock.delete(row);
      return toDtos(stock.findByShopIdOrderByIdAsc(shop.getId()), lang);
   }

   /** «Вы часто отвечаете „Есть“ на … — добавить?»: от трёх ответов за 60 дней, чего ещё нет в списке. */
   @Transactional(readOnly = true)
   public List<StockSuggestionDto> suggestions(Long userId, Lang lang) {
      Shop shop = access.requireMember(userId).shop();
      List<ShopStockRepository.SuggestionRow> rows = stock.suggestions(shop.getId());
      Map<Long, PartHint> byId = hintsById(rows.stream().map(ShopStockRepository.SuggestionRow::getHintId).toList());
      return rows.stream()
            .filter(row -> byId.containsKey(row.getHintId()))
            .map(row -> new StockSuggestionDto(row.getHintId(), byId.get(row.getHintId()).text(lang),
                  row.getBrandId(), brandName(row.getBrandId()), (int) row.getAnswers()))
            .toList();
   }

   /** Регистрация (10): список проверяется до сохранения магазина, пишется после. */
   public void store(Long shopId, List<StockKey> keys) {
      stock.saveAll(keys.stream().map(key -> new ShopStockItem(shopId, key, StockSource.SELF)).toList());
   }

   /**
    * Проверка и чистка списка: деталь из справочника (не скрыта), марка — из марок магазина, модель — этой
    * марки. Повторы убираются, порядок сохраняется.
    */
   public List<StockKey> normalize(List<StockItemInput> items, Set<Long> shopBrandIds) {
      if (items == null || items.isEmpty()) {
         return List.of();
      }
      if (items.size() > MAX_ITEMS) {
         throw new BadRequestException("STOCK_FULL", "Не больше " + MAX_ITEMS + " позиций");
      }
      Map<Long, PartHint> byId = hintsById(items.stream().map(StockItemInput::hintId).toList());
      LinkedHashSet<StockKey> keys = new LinkedHashSet<>();
      for (StockItemInput item : items) {
         PartHint hint = byId.get(item.hintId());
         if (hint == null || !hint.isActive()) {
            throw new BadRequestException("UNKNOWN_PART_HINT", "Нет такой детали: " + item.hintId());
         }
         if (item.brandId() != null && !shopBrandIds.contains(item.brandId())) {
            throw new BadRequestException("BRAND_NOT_IN_SHOP",
                  "Сначала добавьте эту марку в «Марки, которые продаю»");
         }
         if (item.modelId() != null) {
            if (item.brandId() == null) {
               throw new BadRequestException("MODEL_WITHOUT_BRAND", "Модель — только вместе с маркой");
            }
            CarModel model = modelOrNull(item.modelId());
            if (model == null || !Objects.equals(model.getBrandId(), item.brandId())) {
               throw new BadRequestException("UNKNOWN_MODEL", "Нет такой модели у этой марки");
            }
         }
         keys.add(new StockKey(item.hintId(), item.brandId(), item.modelId()));
      }
      return List.copyOf(keys);
   }

   // ─────────────────────── покупатель ───────────────────────

   /**
    * «Обычно есть у N магазинов» по детали и машине. brandId / modelId null — без машины.
    * marketId null — все рынки, 0 — вне рынков.
    */
   @Transactional(readOnly = true)
   public StockingShopsDto stocking(Long hintId, Long brandId, Long modelId, Long marketId, Integer limit) {
      int size = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
      List<Long> ids = stock.stockingShopIds(hintId, brandId == null ? 0 : brandId,
            brandId == null || modelId == null ? 0 : modelId, marketId == null ? -1 : marketId);
      List<Long> page = ids.subList(0, Math.min(size, ids.size()));
      Map<Long, Shop> byId = shops.findAllById(page).stream().collect(Collectors.toMap(Shop::getId, Function.identity()));
      List<Shop> ordered = page.stream().map(byId::get).filter(Objects::nonNull).toList();
      return new StockingShopsDto(ids.size(), ordered.stream().map(mapper::card).toList(),
            ordered.stream().map(Shop::getContainerId).filter(Objects::nonNull).toList());
   }

   // ─────────────────────── служебное ───────────────────────

   private List<StockItemDto> toDtos(List<ShopStockItem> rows, Lang lang) {
      Map<Long, PartHint> byId = hintsById(rows.stream().map(ShopStockItem::getHintId).toList());
      List<StockItemDto> result = new ArrayList<>(rows.size());
      for (ShopStockItem row : rows) {
         PartHint hint = byId.get(row.getHintId());
         if (hint == null || !hint.isActive()) {
            continue; // деталь скрыли в админке — строку не показываем, покупателю она тоже не найдётся
         }
         CarModel model = row.getModelId() == null ? null : modelOrNull(row.getModelId());
         result.add(new StockItemDto(row.getId(), row.getHintId(), hint.text(lang), hint.getCategoryId(),
               row.getBrandId(), brandName(row.getBrandId()), row.getModelId(), model == null ? null : model.label(),
               row.getSource()));
      }
      return result;
   }

   private Map<Long, PartHint> hintsById(Collection<Long> ids) {
      return hints.findAllById(Set.copyOf(ids)).stream().collect(Collectors.toMap(PartHint::getId, Function.identity()));
   }

   private String brandName(Long brandId) {
      if (brandId == null) {
         return null;
      }
      try {
         return directory.brand(brandId).getName();
      } catch (ApiException hidden) {
         return null;
      }
   }

   private CarModel modelOrNull(Long modelId) {
      try {
         return directory.model(modelId);
      } catch (ApiException hidden) {
         return null;
      }
   }
}
