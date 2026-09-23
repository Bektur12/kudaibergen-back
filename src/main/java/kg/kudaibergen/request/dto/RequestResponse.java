package kg.kudaibergen.request.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.common.PartCategory;
import kg.kudaibergen.request.entity.Request;
import kg.kudaibergen.request.entity.RequestStatus;

public record RequestResponse(Long id, PartCategory category, String description, String car,
                              Integer budgetMin, Integer budgetMax, String currency, String city,
                              boolean isUrgent, RequestStatus status, int offerCount,
                              Instant createdAt, Instant expiresAt, List<OfferSummary> offers) {

   public static RequestResponse of(Request request, List<OfferSummary> offers) {
      return new RequestResponse(request.getId(), request.getCategory(), request.getDescription(),
            request.getCarText(), request.getBudgetMin(), request.getBudgetMax(), request.getCurrency(),
            request.getCity(), request.isUrgent(), request.getStatus(), request.getOfferCount(),
            request.getCreatedAt(), request.getExpiresAt(), offers);
   }
}
