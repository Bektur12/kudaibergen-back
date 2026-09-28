package kg.kudaibergen.shop;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.catalog.PartRepository;
import kg.kudaibergen.catalog.entity.PartStatus;
import kg.kudaibergen.category.Category;
import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.shop.dto.MyShopDto;
import kg.kudaibergen.shop.dto.ShopCardDto;
import kg.kudaibergen.shop.dto.ShopPublicDto;
import kg.kudaibergen.shop.dto.VerificationDto;
import kg.kudaibergen.shop.entity.MemberRole;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Component;

/** Сборка DTO магазина: место из карты, марки и категории из справочников, «открыто сейчас». */
@Component
public class ShopMapper {

   private final MarketMapService market;
   private final VehicleDirectory directory;
   private final CategoryService categories;
   private final ShopHours hours;
   private final MediaService media;
   private final PartRepository parts;

   public ShopMapper(MarketMapService market, VehicleDirectory directory, CategoryService categories,
                     ShopHours hours, MediaService media, PartRepository parts) {
      this.market = market;
      this.directory = directory;
      this.categories = categories;
      this.hours = hours;
      this.media = media;
      this.parts = parts;
   }

   public ShopCardDto card(Shop shop) {
      return new ShopCardDto(shop.getId(), shop.getName(), avatarUrl(shop), shop.getRating(), shop.getReviewsCount(),
            location(shop.getContainerId()), hours.state(shop));
   }

   public ShopPublicDto publicProfile(Shop shop, boolean favorite, Lang lang) {
      List<PhotoDto> photos = List.copyOf(media.photos(shop.getPhotoIds()).values());
      return new ShopPublicDto(shop.getId(), shop.getName(), avatarUrl(shop), shop.getRating(),
            shop.getReviewsCount(), location(shop.getContainerId()), hours.state(shop), brands(shop),
            categories(shop, lang), shop.isPhoneVisible() ? shop.getPhone() : null, favorite, photos,
            new ShopPublicDto.Counts(parts.countByShopIdAndStatus(shop.getId(), PartStatus.ACTIVE), photos.size(),
                  shop.getReviewsCount()));
   }

   public MyShopDto mine(Shop shop, MemberRole role, VerificationDto verification, int staffCount, Lang lang) {
      return new MyShopDto(shop.getId(), shop.getName(), avatarUrl(shop), shop.getStatus(), shop.getBlockReason(), role,
            location(shop.getContainerId()),
            shop.getPendingContainerId() == null ? null : location(shop.getPendingContainerId()),
            verification, hours.state(shop), shop.isOpen(), shop.getPhone(), shop.isPhoneVisible(), shop.getRating(),
            shop.getReviewsCount(), brands(shop), categories(shop, lang), staffCount,
            List.copyOf(media.photos(shop.getPhotoIds()).values()));
   }

   /** Аватар — превью 320 px; null — клиент рисует первую букву названия. */
   public String avatarUrl(Shop shop) {
      return media.thumbUrl(shop.getAvatarMediaId());
   }

   /** Первые фото места — блок продавца на карточке запчасти (29). */
   public List<PhotoDto> photos(Shop shop, int limit) {
      return List.copyOf(media.photos(shop.getPhotoIds().stream().limit(limit).toList()).values());
   }

   public LocationDto location(Long containerId) {
      return market.location(containerId);
   }

   /** Марки в порядке справочника: популярные первыми (логотипы в профиле, «до 5 + +N»). */
   private List<BrandDto> brands(Shop shop) {
      return directory.brands().stream()
            .filter(brand -> shop.getBrandIds().contains(brand.getId()))
            .map(BrandDto::of)
            .toList();
   }

   private List<CategoryDto> categories(Shop shop, Lang lang) {
      Map<Long, Category> byId = categories.byId();
      return shop.getCategoryIds().stream()
            .map(byId::get)
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing(Category::getSortOrder))
            .map(category -> CategoryDto.of(category, lang))
            .toList();
   }

   /** Для списков: брендов не нужно, только проверка, что id существуют. */
   public boolean brandExists(Long id) {
      return directory.brands().stream().map(Brand::getId).anyMatch(id::equals);
   }
}
