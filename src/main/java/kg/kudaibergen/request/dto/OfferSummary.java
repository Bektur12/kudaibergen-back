package kg.kudaibergen.request.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Предложение глазами покупателя. Заполняется пакетом offer через RequestOffersView. */
public record OfferSummary(Long id, Long storeId, String storeName, BigDecimal storeRating,
                           Integer price, String currency, String comment, Short deliveryDays,
                           String status, Instant createdAt) {
}
