package kg.kudaibergen.store.dto;

import java.util.List;

public record WorkHours(String open, String close, List<String> days) {
}
