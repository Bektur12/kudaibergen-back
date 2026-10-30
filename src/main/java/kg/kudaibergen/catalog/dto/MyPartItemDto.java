package kg.kudaibergen.catalog.dto;

import java.util.List;

import kg.kudaibergen.catalog.entity.PartStatus;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.media.PhotoDto;
import org.springframework.lang.Nullable;

/**
 * Строка «Мои запчасти» (24) и плитка в профиле продавца: фото, название, мини-логотипы + модели, цена, «В наличии» /
 * «Нет». videoPosterUrl — обложка ролика у товара с видео (null — видео нет).
 */
public record MyPartItemDto(Long id, PartStatus status, @Nullable String title, @Nullable PhotoDto mainPhoto, List<BrandDto> brands,
                            List<String> fitmentLabels, @Nullable Integer price, int quantity, StockStatus stockStatus,
                            int views, @Nullable String videoPosterUrl) {
}
