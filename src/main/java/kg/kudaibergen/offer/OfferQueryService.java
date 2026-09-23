package kg.kudaibergen.offer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.offer.entity.Offer;
import kg.kudaibergen.request.RequestOffersView;
import kg.kudaibergen.request.dto.OfferSummary;
import kg.kudaibergen.store.StoreRepository;
import kg.kudaibergen.store.entity.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Реализация контракта, который объявил соседний пакет: предложения для карточки запроса. */
@Service
public class OfferQueryService implements RequestOffersView {

   private final OfferRepository offers;
   private final StoreRepository stores;

   public OfferQueryService(OfferRepository offers, StoreRepository stores) {
      this.offers = offers;
      this.stores = stores;
   }

   @Override
   @Transactional(readOnly = true)
   public List<OfferSummary> byRequest(Long requestId) {
      List<Offer> found = offers.findByRequestIdOrderByCreatedAtAsc(requestId);
      if (found.isEmpty()) {
         return List.of();
      }
      Map<Long, Store> storeById = stores.findAllById(
                  found.stream().map(Offer::getStoreId).distinct().toList()).stream()
            .collect(Collectors.toMap(Store::getId, Function.identity()));

      return found.stream()
            .map(offer -> {
               Store store = storeById.get(offer.getStoreId());
               return new OfferSummary(offer.getId(), offer.getStoreId(),
                     store == null ? null : store.getName(),
                     store == null ? BigDecimal.ZERO : store.getRating(),
                     offer.getPrice(), offer.getCurrency(), offer.getComment(), offer.getDeliveryDays(),
                     offer.getStatus().name(), offer.getCreatedAt());
            })
            .toList();
   }
}
