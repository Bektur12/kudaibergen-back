package kg.kudaibergen.store.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import kg.kudaibergen.common.PartCategory;
import kg.kudaibergen.store.entity.VerificationStatus;

public record StoreDetailsResponse(Long id, String name, String businessType, String description,
                                   BigDecimal rating, int reviewCount, int totalDeals,
                                   VerificationStatus verificationStatus, Set<PartCategory> categories,
                                   List<BranchResponse> branches) {
}
