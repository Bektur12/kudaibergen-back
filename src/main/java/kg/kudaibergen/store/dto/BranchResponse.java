package kg.kudaibergen.store.dto;

import java.math.BigDecimal;

public record BranchResponse(Long id, String address, String city, String phone,
                             BigDecimal latitude, BigDecimal longitude, WorkHours workHours) {
}
