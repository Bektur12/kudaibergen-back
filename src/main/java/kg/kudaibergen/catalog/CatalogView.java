package kg.kudaibergen.catalog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.catalog.dto.CarFilter;
import kg.kudaibergen.catalog.dto.FitmentDto;
import kg.kudaibergen.catalog.dto.MyPartItemDto;
import kg.kudaibergen.catalog.dto.PartCardDto;
import kg.kudaibergen.catalog.dto.PartDetailDto;
import kg.kudaibergen.catalog.entity.Fitment;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.category.Category;
import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.dto.ShopCardDto;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Component;

/** Сборка DTO каталога пачкой: главные фото, магазины, избранное, «Подходит» и подписи машин. */
@Component
public class CatalogView {

   private final PartRepository parts;
   private final MediaService media;
   private final ShopRepository shops;
   private final ShopMapper shopMapper;
   private final FavoritePartRepository favorites;
   private final VehicleDirectory directory;
   private final CategoryService categories;

   public CatalogView(PartRepository parts, MediaService media, ShopRepository shops, ShopMapper shopMapper,
                      FavoritePartRepository favorites, VehicleDirectory directory, CategoryService categories) {
      this.parts = parts;
      this.media = media;
      this.shops = shops;
      this.shopMapper = shopMapper;
      this.favorites = favorites;
      this.directory = directory;
      this.categories = categories;
   }

   /** Карточки в порядке id; пропавшие между поиском и загрузкой — пропускаются. */
   public List<PartCardDto> cards(List<Long> ids, CarFilter car, Long viewerId) {
      Map<Long, Part> byId = parts.findAllById(ids).stream()
            .collect(Collectors.toMap(Part::getId, Function.identity()));
      List<Part> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
      Map<Long, PhotoDto> photos = media.photos(ordered.stream()
            .filter(part -> !part.getPhotoIds().isEmpty()).map(part -> part.getPhotoIds().get(0)).toList());
      Map<Long, ShopCardDto> shopCards = shopCards(ordered.stream().map(Part::getShopId).toList());
      Set<Long> favorite = favorites.favoriteAmong(viewerId, ids);
      return ordered.stream()
            .map(part -> new PartCardDto(part.getId(), part.getTitle(), part.getPrice(), part.getCondition(),
                  part.inStock(), part.getPhotoIds().isEmpty() ? null : photos.get(part.getPhotoIds().get(0)),
                  car == null ? null : Fits.anyFits(part.getFitments(), car),
                  car != null && Fits.anyExact(part.getFitments(), car), favorite.contains(part.getId()),
                  shopCards.get(part.getShopId())))
            .toList();
   }

   public PartDetailDto detail(Part part, CarFilter car, Long viewerId, Lang lang) {
      Shop shop = shops.findById(part.getShopId()).orElseThrow();
      PartDetailDto.FitDto fit = car == null ? null
            : new PartDetailDto.FitDto(car.label(), Fits.anyFits(part.getFitments(), car));
      return new PartDetailDto(part.getId(), part.getStatus(), part.getTitle(), part.getPrice(), part.getCondition(),
            part.getQuantity(), part.inStock(), category(part.getCategoryId(), lang), part.getManufacturer(),
            part.getOemNumber(), part.getSide(), part.getPosition(),
            List.copyOf(media.photos(part.getPhotoIds()).values()), fitments(part.getFitments(), lang), fit,
            !favorites.favoriteAmong(viewerId, List.of(part.getId())).isEmpty(), shopMapper.card(shop),
            shopMapper.photos(shop, 3),
            part.getPublishedAt(), part.getUpdatedAt());
   }

   public List<MyPartItemDto> myItems(List<Part> page, Lang lang) {
      Map<Long, PhotoDto> photos = media.photos(page.stream()
            .filter(part -> !part.getPhotoIds().isEmpty()).map(part -> part.getPhotoIds().get(0)).toList());
      return page.stream().map(part -> {
         List<BrandDto> brands = part.getFitments().stream().map(Fitment::getBrandId).distinct()
               .map(directory::brand).map(BrandDto::of).toList();
         List<String> labels = fitments(part.getFitments(), lang).stream().map(FitmentDto::label).toList();
         return new MyPartItemDto(part.getId(), part.getStatus(), part.getTitle(),
               part.getPhotoIds().isEmpty() ? null : photos.get(part.getPhotoIds().get(0)), brands, labels,
               part.getPrice(), part.getQuantity(), part.inStock(), part.getViewsCount());
      }).toList();
   }

   /** Чипы «Toyota Camry 50 · 2011–2017», «Lexus · все модели». */
   public List<FitmentDto> fitments(List<Fitment> list, Lang lang) {
      List<FitmentDto> result = new ArrayList<>(list.size());
      for (Fitment fitment : list) {
         Brand brand = directory.brand(fitment.getBrandId());
         String modelLabel = fitment.getModelId() == null ? null : directory.model(fitment.getModelId()).label();
         String label = brand.getName() + (modelLabel == null ? " · " + allModels(lang) : " " + modelLabel)
               + years(fitment.getYearFrom(), fitment.getYearTo(), lang);
         result.add(new FitmentDto(BrandDto.of(brand), fitment.getModelId(), modelLabel, fitment.getYearFrom(),
               fitment.getYearTo(), label));
      }
      return result;
   }

   private Map<Long, ShopCardDto> shopCards(Collection<Long> shopIds) {
      Map<Long, ShopCardDto> cards = new LinkedHashMap<>();
      shops.findAllById(shopIds.stream().distinct().toList())
            .forEach(shop -> cards.put(shop.getId(), shopMapper.card(shop)));
      return cards;
   }

   private CategoryDto category(Long categoryId, Lang lang) {
      if (categoryId == null) {
         return null;
      }
      Category category = categories.byId().get(categoryId);
      return category == null ? null : CategoryDto.of(category, lang);
   }

   private static String allModels(Lang lang) {
      return lang == Lang.KG ? "бардык моделдер" : "все модели";
   }

   /** « · 2011–2017», « · с 2011», « · до 2017» или пусто. */
   static String years(Integer from, Integer to, Lang lang) {
      if (from == null && to == null) {
         return "";
      }
      if (from != null && to != null) {
         return " · " + from + "–" + to;
      }
      boolean kg = lang == Lang.KG;
      return from != null ? " · " + (kg ? from + " жылдан" : "с " + from) : " · " + (kg ? to + " жылга чейин" : "до " + to);
   }
}
