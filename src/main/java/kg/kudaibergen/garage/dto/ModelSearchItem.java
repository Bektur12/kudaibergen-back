package kg.kudaibergen.garage.dto;

/** Результат поиска «Марка или модель» (экран 28): модель вместе с маркой. */
public record ModelSearchItem(ModelDto model, BrandDto brand) {
}
