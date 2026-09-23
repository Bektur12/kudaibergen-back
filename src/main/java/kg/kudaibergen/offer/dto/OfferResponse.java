package kg.kudaibergen.offer.dto;

import java.time.Instant;

import kg.kudaibergen.offer.entity.Offer;
import kg.kudaibergen.offer.entity.OfferStatus;

public record OfferResponse(Long id, Long requestId, Long storeId, Integer price, String currency,
                            String comment, Short deliveryDays, OfferStatus status, Instant createdAt) {

   public static OfferResponse of(Offer offer) {
      return new OfferResponse(offer.getId(), offer.getRequestId(), offer.getStoreId(), offer.getPrice(),
            offer.getCurrency(), offer.getComment(), offer.getDeliveryDays(), offer.getStatus(),
            offer.getCreatedAt());
   }
}
