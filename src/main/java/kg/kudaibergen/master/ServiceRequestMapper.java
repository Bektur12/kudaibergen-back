package kg.kudaibergen.master;

import java.util.List;

import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.garage.entity.FuelType;
import kg.kudaibergen.master.dto.MasterCardDto;
import kg.kudaibergen.master.dto.MasterFeedItemDto;
import kg.kudaibergen.master.dto.ServiceCarDto;
import kg.kudaibergen.master.dto.ServiceOfferDto;
import kg.kudaibergen.master.dto.ServiceRequestDetailDto;
import kg.kudaibergen.master.dto.ServiceRequestState;
import kg.kudaibergen.master.dto.ServiceRequestSummaryDto;
import kg.kudaibergen.master.dto.ServiceTypeDto;
import kg.kudaibergen.master.entity.ServiceOffer;
import kg.kudaibergen.master.entity.ServiceRecipient;
import kg.kudaibergen.master.entity.ServiceRequest;
import kg.kudaibergen.master.entity.ServiceType;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Component;

/** DTO заявок: подпись машины, состояние для клиента, отклики, строка ленты мастера. */
@Component
public class ServiceRequestMapper {

   private final VehicleDirectory directory;
   private final ServiceCatalog catalog;
   private final MediaService media;

   public ServiceRequestMapper(VehicleDirectory directory, ServiceCatalog catalog, MediaService media) {
      this.directory = directory;
      this.catalog = catalog;
      this.media = media;
   }

   /** «Toyota Camry 50 · 2012 · 2.5 бензин». */
   public ServiceCarDto car(ServiceRequest request) {
      Brand brand = directory.brand(request.getBrandId());
      CarModel model = request.getModelId() == null ? null : directory.model(request.getModelId());
      StringBuilder label = new StringBuilder(brand.getName());
      if (model != null) {
         label.append(' ').append(model.label());
      }
      if (request.getYear() != null) {
         label.append(" · ").append(request.getYear());
      }
      String engine = engine(request.getEngineVolume(), request.getFuel());
      if (engine != null) {
         label.append(" · ").append(engine);
      }
      return new ServiceCarDto(request.getCarId(), BrandDto.of(brand), model == null ? null : model.getId(),
            model == null ? null : model.label(), request.getYear() == null ? null : (int) request.getYear(),
            request.getEngineVolume(), request.getFuel(), request.getOrigin(), label.toString());
   }

   /** «2.5 бензин», «1.5», «дизель». */
   static String engine(java.math.BigDecimal volume, FuelType fuel) {
      String fuelText = fuel == null ? null : switch (fuel) {
         case PETROL -> "бензин";
         case DIESEL -> "дизель";
         case LPG_PETROL -> "газ-бензин";
         case HYBRID -> "гибрид";
         case ELECTRIC -> "электро";
      };
      if (volume == null) {
         return fuelText;
      }
      return fuelText == null ? volume.toPlainString() : volume.toPlainString() + " " + fuelText;
   }

   public static ServiceRequestState state(ServiceRequest request) {
      boolean offers = request.getCanHelpCount() > 0;
      return switch (request.getStatus()) {
         case ACTIVE -> offers ? ServiceRequestState.HAS_OFFERS : ServiceRequestState.WAITING;
         case EXPIRED -> offers ? ServiceRequestState.EXPIRED : ServiceRequestState.NO_OFFERS;
         case CLOSED -> ServiceRequestState.CLOSED;
      };
   }

   public ServiceTypeDto service(ServiceRequest request, Lang lang) {
      return ServiceCatalog.dto(type(request), lang);
   }

   private ServiceType type(ServiceRequest request) {
      return catalog.require(request.getService());
   }

   public ServiceRequestSummaryDto summary(ServiceRequest request, Lang lang) {
      return new ServiceRequestSummaryDto(request.getId(), service(request, lang), request.getDescription(),
            car(request).label(), request.getStatus(), state(request), request.getCanHelpCount(),
            request.getCreatedAt(), request.getExpiresAt());
   }

   public ServiceRequestDetailDto detail(ServiceRequest request, long seenCount, Lang lang) {
      ServiceType type = type(request);
      return new ServiceRequestDetailDto(request.getId(), ServiceCatalog.dto(type, lang), car(request),
            request.getDescription(), photos(request.getPhotoIds()), media.items(request.getPhotoIds()),
            request.getWhen(), request.getAtTime(), request.getWhere(), request.getLat(), request.getLng(), request.getAddress(), request.getRadiusKm(),
            request.getStatus(), state(request), request.getDuration(), request.getExpiresAt(),
            request.getExtendedTimes(), request.canExtend(), request.canWiden(), request.getRecipientsCount(),
            seenCount, request.getCanHelpCount(), request.getClosedWithMasterId(), type.isUrgent(),
            request.getCreatedAt(), request.getClosedAt());
   }

   public ServiceOfferDto offer(ServiceOffer offer, MasterCardDto master, int distanceM, Long chatId) {
      return new ServiceOfferDto(offer.getId(), offer.getRequestId(), master, offer.getAnswer(), offer.getPriceFrom(),
            offer.getAvailableAt(), offer.getMessage(), distanceM, chatId, offer.getCreatedAt());
   }

   public MasterFeedItemDto feedItem(ServiceRequest request, ServiceRecipient recipient, String buyerName,
                                     ServiceOfferDto myOffer, Lang lang) {
      ServiceType type = type(request);
      return new MasterFeedItemDto(request.getId(), ServiceCatalog.dto(type, lang), car(request),
            request.getDescription(), photos(request.getPhotoIds()), media.items(request.getPhotoIds()),
            request.getWhen(), request.getAtTime(), request.getWhere(), request.getAddress(), request.getLat(), request.getLng(), recipient.getDistanceM(),
            buyerName, request.getStatus(), recipient.getMasterId().equals(request.getClosedWithMasterId()),
            type.isUrgent(), request.getCreatedAt(), recipient.getNotifiedAt(), request.getExpiresAt(),
            recipient.getSeenAt() != null, myOffer);
   }

   private List<PhotoDto> photos(List<Long> ids) {
      return List.copyOf(media.photos(ids).values());
   }
}
