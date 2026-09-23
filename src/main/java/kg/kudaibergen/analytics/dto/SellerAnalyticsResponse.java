package kg.kudaibergen.analytics.dto;

import java.util.List;

public record SellerAnalyticsResponse(long requestsReceived, long requestsAnswered, double responseRate,
                                      long requestsMissed, long missedBudgetSum, Integer avgResponseMinutes,
                                      long dealsClosed, List<CategoryDemand> topDemandedCategories) {
}
