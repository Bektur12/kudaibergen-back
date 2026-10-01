package kg.kudaibergen.master.dto;

/** «Заявку получат 12 мастеров — СТО, которые чинят Toyota» (36). */
public record ServiceEstimateDto(int recipients, String label) {
}
