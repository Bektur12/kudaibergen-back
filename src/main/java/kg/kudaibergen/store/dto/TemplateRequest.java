package kg.kudaibergen.store.dto;

import jakarta.validation.constraints.Size;

public record TemplateRequest(@Size(max = 60) String title,
                              @Size(max = 2000) String body,
                              Short sortOrder) {
}
