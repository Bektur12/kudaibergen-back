package kg.kudaibergen.catalog.dto;

import java.util.List;

import kg.kudaibergen.catalog.entity.PartStatus;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.media.PhotoDto;

/** Строка «Мои запчасти» (24): фото, название, мини-логотипы + модели, цена, «В наличии» / «Нет». */
public record MyPartItemDto(Long id, PartStatus status, String title, PhotoDto mainPhoto, List<BrandDto> brands,
                            List<String> fitmentLabels, Integer price, int quantity, StockStatus stockStatus,
                            int views) {
}
