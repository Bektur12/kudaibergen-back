package kg.kudaibergen.catalog.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.catalog.entity.PartPosition;
import kg.kudaibergen.catalog.entity.PartSide;
import kg.kudaibergen.catalog.entity.PartStatus;
import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.request.entity.PartCondition;
import kg.kudaibergen.shop.dto.ShopCardDto;

/**
 * Карточка запчасти (29) и форма редактирования (26). fit — плашка «Подходит к вашей Camry 50 · 2012»
 * (fits = true) или «Не указано для вашей машины» (false); null — машина не выбрана.
 * shopPhotos — 3 фото места в блоке продавца.
 */
public record PartDetailDto(Long id, PartStatus status, String title, Integer price, PartCondition condition,
                            int quantity, boolean inStock, CategoryDto category, String manufacturer,
                            String oemNumber, PartSide side, PartPosition position, List<PhotoDto> photos,
                            List<FitmentDto> fitments, FitDto fit, boolean isFavorite, ShopCardDto shop,
                            List<PhotoDto> shopPhotos,
                            Instant publishedAt, Instant updatedAt) {

   public record FitDto(String carLabel, boolean fits) {
   }
}
