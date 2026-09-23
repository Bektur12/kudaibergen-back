package kg.kudaibergen.analytics;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.analytics.dto.AnalyticsPeriod;
import kg.kudaibergen.analytics.dto.CategoryDemand;
import kg.kudaibergen.analytics.dto.SellerAnalyticsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

   private final AnalyticsRepository analytics;

   public AnalyticsService(AnalyticsRepository analytics) {
      this.analytics = analytics;
   }

   @Transactional(readOnly = true)
   public SellerAnalyticsResponse forStore(Long storeId, AnalyticsPeriod period) {
      AnalyticsPeriod effective = period == null ? AnalyticsPeriod.WEEK : period;
      Instant from = Instant.now().minus(effective.duration());

      AnalyticsRepository.Summary summary = analytics.summary(storeId, from);
      List<CategoryDemand> categories = analytics.categories(storeId, from);
      long deals = analytics.dealsClosed(storeId, from);

      double responseRate = summary.requestsReceived() == 0
            ? 0
            : Math.round((double) summary.requestsAnswered() / summary.requestsReceived() * 100) / 100.0;

      return new SellerAnalyticsResponse(summary.requestsReceived(), summary.requestsAnswered(),
            responseRate, summary.requestsMissed(), summary.missedBudgetSum(),
            summary.avgResponseMinutes(), deals, categories);
   }
}
