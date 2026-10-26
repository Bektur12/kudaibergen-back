package kg.kudaibergen.catalog.dto;

import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.media.VideoDto;
import kg.kudaibergen.request.entity.PartCondition;
import kg.kudaibergen.shop.dto.ShopBriefDto;
import org.springframework.lang.Nullable;

/**
 * Карточка в сетке (27, 30): главное фото, сердечко, цена, название, «✓ Подходит», магазин «Ряд 14 · Бокс 12».
 * Цена — целые сомы, currency всегда KGS. fits = null — машина не выбрана; exactModel — подходит именно
 * к этой модели (не «ко всем моделям марки»). video — видео товара (обложка вместо фото, если фото нет),
 * shipsToRegions — «Доставка по КР».
 */
public record PartCardDto(Long id, String title, int price, String currency, PartCondition condition,
                          StockStatus stockStatus, @Nullable PhotoDto mainPhoto, @Nullable Boolean fits, boolean exactModel,
                          boolean isFavorite, ShopBriefDto shop, @Nullable VideoDto video, boolean shipsToRegions) {

   public static final String KGS = "KGS";
}
