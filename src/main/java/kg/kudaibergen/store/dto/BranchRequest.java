package kg.kudaibergen.store.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Size;

public record BranchRequest(@Size(max = 200) String address,
                            @Size(max = 80) String city,
                            @Size(max = 20) String phone,
                            BigDecimal latitude,
                            BigDecimal longitude,
                            WorkHours workHours) {
}
