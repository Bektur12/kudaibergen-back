package kg.kudaibergen.catalog.dto;

/**
 * Машина, для которой ищут: из гаража или выбранная без гаража (28). modelId null — «Все модели»
 * марки, year null — любой год. label — «Camry 50 · 2012» для заголовка и плашки «Подходит».
 */
public record CarFilter(Long brandId, Long modelId, Integer year, String label) {
}
