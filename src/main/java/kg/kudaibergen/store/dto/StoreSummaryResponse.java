package kg.kudaibergen.store.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import kg.kudaibergen.common.PartCategory;
import kg.kudaibergen.store.entity.VerificationStatus;

public record StoreSummaryResponse(Long id, String name, String businessType, BigDecimal rating,
                                   int reviewCount, int totalDeals, VerificationStatus verificationStatus,
                                   Set<PartCategory> categories, List<String> cities) {
}
