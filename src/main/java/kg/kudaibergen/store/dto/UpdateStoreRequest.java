package kg.kudaibergen.store.dto;

import jakarta.validation.constraints.Size;

public record UpdateStoreRequest(@Size(max = 120) String name,
                                 @Size(max = 4000) String description,
                                 @Size(max = 40) String businessType) {
}
