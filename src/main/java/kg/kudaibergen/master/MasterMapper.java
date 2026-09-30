package kg.kudaibergen.master;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.master.dto.MasterCardDto;
import kg.kudaibergen.master.dto.MasterPublicDto;
import kg.kudaibergen.master.dto.MyMasterDto;
import kg.kudaibergen.master.entity.Master;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Component;

/** DTO мастера: карточка, свой профиль, профиль для клиента. */
@Component
public class MasterMapper {

   private final MediaService media;
   private final VehicleDirectory directory;
   private final ServiceCatalog catalog;
   private final MasterHours hours;

   public MasterMapper(MediaService media, VehicleDirectory directory, ServiceCatalog catalog, MasterHours hours) {
      this.media = media;
      this.directory = directory;
      this.catalog = catalog;
      this.hours = hours;
   }

   public MasterCardDto card(Master master) {
      return card(master, media.thumbUrl(master.getAvatarMediaId()));
   }

   public MasterCardDto card(Master master, String avatarUrl) {
      return new MasterCardDto(master.getId(), master.getName(), avatarUrl, master.getRating(),
            master.getReviewsCount(), master.getAddress(), master.isMobile(), hours.openNow(master), master.getPhone());
   }

   /** Карточки пачкой: аватары одним запросом. */
   public Map<Long, MasterCardDto> cards(List<Master> masters) {
      Map<Long, String> avatars = media.thumbUrls(masters.stream().map(Master::getAvatarMediaId).toList());
      return masters.stream().collect(java.util.stream.Collectors.toMap(Master::getId,
            master -> card(master, avatars.get(master.getAvatarMediaId()))));
   }

   public MyMasterDto mine(Master master, Lang lang) {
      return new MyMasterDto(master.getId(), master.getPublicId(), master.getName(),
            media.thumbUrl(master.getAvatarMediaId()), master.getStatus(), master.getBlockReason(),
            catalog.dtos(master.getServices(), lang), master.isAllBrands(), brands(master), origins(master),
            master.getAddress(), master.getLat(), master.getLng(), master.getRadiusKm(), master.isMobile(),
            master.isAccepting(), hours.state(master), master.getPhone(), master.getRating(), master.getReviewsCount(),
            photos(master));
   }

   public MasterPublicDto publicProfile(Master master, Lang lang) {
      return new MasterPublicDto(master.getId(), master.getPublicId(), master.getName(),
            media.thumbUrl(master.getAvatarMediaId()), master.getRating(), master.getReviewsCount(),
            catalog.dtos(master.getServices(), lang), master.isAllBrands(), brands(master), origins(master),
            master.getAddress(), master.getLat(), master.getLng(), master.getRadiusKm(), master.isMobile(),
            hours.state(master), master.getPhone(), photos(master));
   }

   /** Марки в порядке справочника: популярные первыми. */
   private List<BrandDto> brands(Master master) {
      return directory.brands().stream().filter(brand -> master.getBrandIds().contains(brand.getId()))
            .map(BrandDto::of).toList();
   }

   private static List<CarOrigin> origins(Master master) {
      return Arrays.stream(CarOrigin.values()).filter(master.getOrigins()::contains).toList();
   }

   private List<PhotoDto> photos(Master master) {
      return List.copyOf(media.photos(master.getPhotoIds()).values());
   }
}
