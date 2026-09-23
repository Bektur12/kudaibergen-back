package kg.kudaibergen.store.dto;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import kg.kudaibergen.common.PartCategory;

public record SetCategoriesRequest(@NotNull(message = "Укажите категории") Set<PartCategory> categories) {
}
