package kg.kudaibergen.shop.stock;

/** Что именно «всегда есть»: деталь, марка (null — все марки магазина), модель (null — все модели марки). */
public record StockKey(Long hintId, Long brandId, Long modelId) {
}
