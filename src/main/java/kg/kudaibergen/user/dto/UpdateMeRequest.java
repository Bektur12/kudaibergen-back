package kg.kudaibergen.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateMeRequest(@Size(max = 120) String name, @Size(max = 80) String city) {
}
