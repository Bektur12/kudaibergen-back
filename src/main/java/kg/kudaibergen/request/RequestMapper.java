package kg.kudaibergen.request;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.catalog.dto.CarFilter;
import kg.kudaibergen.catalog.dto.PartCardDto;
import kg.kudaibergen.category.Category;
import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.category.CategoryService;
import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.request.dto.IncomingRequestDto;
import kg.kudaibergen.request.dto.ReplyDto;
import kg.kudaibergen.request.dto.RequestCarDto;
import kg.kudaibergen.request.dto.RequestDetailDto;
import kg.kudaibergen.request.dto.RequestState;
import kg.kudaibergen.request.dto.RequestSummaryDto;
import kg.kudaibergen.request.entity.PartRequest;
import kg.kudaibergen.request.entity.RequestRecipient;
import kg.kudaibergen.request.entity.RequestReply;
import kg.kudaibergen.shop.dto.ShopCardDto;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.stereotype.Component;

/** Сборка DTO запросов: подписи машины из справочника, фото, состояние для покупателя, окно правки ответа. */
@Component
public class RequestMapper {

   private final VehicleDirectory directory;
   private final CategoryService categories;
   private final MediaService media;
   private final Duration replyEditWindow;

   public RequestMapper(VehicleDirectory directory, CategoryService categories, MediaService media,
                        AppProperties properties) {
      this.directory = directory;
      this.categories = categories;
      this.media = media;
      this.replyEditWindow = properties.requests().replyEditWindow();
   }

   /** «Toyota Camry 50 · 2012». */
   public RequestCarDto car(PartRequest request) {
      Brand brand = directory.brand(request.getBrandId());
      CarModel model = directory.model(request.getModelId());
      return new RequestCarDto(request.getCarId(), BrandDto.of(brand), model.getId(), model.label(),
            request.getYear(), brand.getName() + " " + model.label() + " · " + request.getYear());
   }

   /** Машина запроса как фильтр каталога: подбор своих запчастей к ответу «Есть». */
   public CarFilter carFilter(PartRequest request) {
      return new CarFilter(request.getBrandId(), request.getModelId(), (int) request.getYear(), car(request).label());
   }

   /** Время вышло без «Есть» — экран 20 «Пока никто не ответил»; с ответами — «Время вышло», можно продлить. */
   public static RequestState state(PartRequest request) {
      boolean answered = request.getHaveCount() > 0;
      return switch (request.getStatus()) {
         case ACTIVE -> answered ? RequestState.HAS_ANSWERS : RequestState.WAITING;
         case EXPIRED -> answered ? RequestState.EXPIRED : RequestState.NO_ANSWERS;
         case CLOSED -> RequestState.CLOSED;
      };
   }

   public RequestSummaryDto summary(PartRequest request) {
      return new RequestSummaryDto(request.getId(), request.getText(), car(request).label(), request.getStatus(),
            state(request), request.getHaveCount(), request.getCreatedAt(), request.getExpiresAt());
   }

   public RequestDetailDto detail(PartRequest request, long seenCount, Lang lang) {
      return new RequestDetailDto(request.getId(), request.getText(), car(request), category(request, lang),
            photos(request.getPhotoIds()), request.getTarget(), request.getTargetRowIds(),
            request.getTargetContainerIds(), request.getStatus(), state(request), request.getDuration(),
            request.getExpiresAt(), request.getExtendedTimes(), request.canExtend(), request.getRecipientsCount(),
            seenCount, request.getHaveCount(), request.getClosedWithShopId(), request.getCreatedAt(),
            request.getClosedAt());
   }

   public ReplyDto reply(RequestReply reply, ShopCardDto shop, Long chatId, PartCardDto part) {
      return new ReplyDto(reply.getId(), reply.getRequestId(), shop, reply.getAnswer(), reply.getCondition(),
            reply.getMessage(), reply.getPrice(), photos(reply.getPhotoIds()), reply.getCreatedAt(),
            reply.getUpdatedAt(), reply.getCreatedAt().plus(replyEditWindow), chatId, part);
   }

   public IncomingRequestDto incoming(PartRequest request, RequestRecipient recipient, String buyerName,
                                      ReplyDto myReply, Lang lang) {
      return new IncomingRequestDto(request.getId(), request.getText(), car(request), category(request, lang),
            photos(request.getPhotoIds()), buyerName, request.getStatus(),
            recipient.getShopId().equals(request.getClosedWithShopId()), request.getCreatedAt(),
            recipient.getNotifiedAt(), request.getExpiresAt(), recipient.getSeenAt() != null, myReply);
   }

   private List<PhotoDto> photos(List<Long> ids) {
      return List.copyOf(media.photos(ids).values());
   }

   private CategoryDto category(PartRequest request, Lang lang) {
      if (request.getCategoryId() == null) {
         return null;
      }
      Map<Long, Category> byId = categories.byId();
      Category category = byId.get(request.getCategoryId());
      return category == null ? null : CategoryDto.of(category, lang);
   }
}
