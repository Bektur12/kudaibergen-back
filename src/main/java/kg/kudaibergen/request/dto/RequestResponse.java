package kg.kudaibergen.request.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.common.PartCategory;
import kg.kudaibergen.request.entity.Request;
import kg.kudaibergen.request.entity.RequestStatus;

public record RequestResponse(Long id, PartCategory category, String description, String car,
                              Integer budgetMin, Integer budgetMax, String currency, String city,
                              boolean isUrgent, RequestStatus status, int offerCount, String photoUrl,
                              Instant createdAt, Instant expiresAt, List<OfferSummary> offers) {

   /** {@code photoUrl} уже резолвлен из ключа хранилища в клиентский URL — см. RequestService. */
   public static RequestResponse of(Request request, List<OfferSummary> offers, String photoUrl) {
      return new RequestResponse(request.getId(), request.getCategory(), request.getDescription(),
            request.getCarText(), request.getBudgetMin(), request.getBudgetMax(), request.getCurrency(),
            request.getCity(), request.isUrgent(), request.getStatus(), request.getOfferCount(), photoUrl,
            request.getCreatedAt(), request.getExpiresAt(), offers);
   }
}
