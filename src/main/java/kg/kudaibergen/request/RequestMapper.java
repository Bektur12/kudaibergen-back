package kg.kudaibergen.request;

import java.time.Duration;
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

/** Сборка DTO запросов: подписи машины из справочника, состояние для покупателя, окно правки ответа. */
@Component
public class RequestMapper {

   private final VehicleDirectory directory;
   private final CategoryService categories;
   private final Duration replyEditWindow;

   public RequestMapper(VehicleDirectory directory, CategoryService categories, AppProperties properties) {
      this.directory = directory;
      this.categories = categories;
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

   public static RequestState state(PartRequest request) {
      return switch (request.getStatus()) {
         case CLOSED -> RequestState.CLOSED;
         case EXPIRED -> RequestState.EXPIRED;
         case OPEN -> request.getHaveCount() > 0 ? RequestState.HAS_ANSWERS
               : request.getNoReplyAt() != null ? RequestState.NO_ANSWERS : RequestState.WAITING;
      };
   }

   public RequestSummaryDto summary(PartRequest request) {
      return new RequestSummaryDto(request.getId(), request.getText(), car(request).label(), request.getStatus(),
            state(request), request.getHaveCount(), request.getCreatedAt());
   }

   public RequestDetailDto detail(PartRequest request, long seenCount, Lang lang) {
      return new RequestDetailDto(request.getId(), request.getText(), car(request), category(request, lang),
            request.getTarget(), request.getTargetRowId(), request.getTargetShopId(), request.getStatus(),
            state(request), request.getRecipientsCount(), seenCount, request.getHaveCount(),
            request.getClosedWithShopId(), request.getCreatedAt(), request.getClosedAt());
   }

   public ReplyDto reply(RequestReply reply, ShopCardDto shop, Long chatId, PartCardDto part) {
      return new ReplyDto(reply.getId(), reply.getRequestId(), shop, reply.getAnswer(), reply.getCondition(),
            reply.getMessage(), reply.getPrice(), reply.getCreatedAt(), reply.getUpdatedAt(),
            reply.getCreatedAt().plus(replyEditWindow), chatId, part);
   }

   public IncomingRequestDto incoming(PartRequest request, RequestRecipient recipient, String buyerName,
                                      ReplyDto myReply, Lang lang) {
      return new IncomingRequestDto(request.getId(), request.getText(), car(request), category(request, lang),
            buyerName, request.getStatus(), recipient.getShopId().equals(request.getClosedWithShopId()),
            request.getCreatedAt(), recipient.getNotifiedAt(), recipient.getSeenAt() != null, myReply);
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
