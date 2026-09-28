package kg.kudaibergen.market.dto;

/** Точка «я здесь» на схеме и радиус круга точности в пикселях. */
public record LocateDto(double x, double y, double radiusPx, boolean insideMarket) {
}
