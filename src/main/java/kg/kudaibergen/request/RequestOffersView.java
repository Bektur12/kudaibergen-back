package kg.kudaibergen.request;

import java.util.List;

import kg.kudaibergen.request.dto.OfferSummary;

/**
 * Предложения по запросу. Контракт объявлен здесь, реализован в пакете offer —
 * так зависимость остаётся односторонней: offer знает про request, но не наоборот.
 */
public interface RequestOffersView {

   List<OfferSummary> byRequest(Long requestId);
}
