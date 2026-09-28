package kg.kudaibergen.catalog.dto;

import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.request.entity.PartCondition;
import kg.kudaibergen.shop.dto.ShopCardDto;

/**
 * Карточка в сетке (27, 30): фото, сердечко, цена, название, «✓ Подходит», магазин «Ряд 14 · Бокс 12».
 * fits = null — машина не выбрана; exactModel — подходит именно к этой модели (не «ко всем моделям марки»).
 */
public record PartCardDto(Long id, String title, int price, PartCondition condition, boolean inStock, PhotoDto photo,
                          Boolean fits, boolean exactModel, boolean isFavorite, ShopCardDto shop) {
}
