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
import org.springframework.lang.Nullable;

/**
 * Карточка запчасти (29) и форма редактирования (26). fit — плашка «Подходит к вашей Camry 50 · 2012»
 * (fits = true) или «Не указано для вашей машины» (false); null — машина не выбрана.
 * shopPhotos — 3 фото места в блоке продавца. publicId — для ссылки «Поделиться» (GET /parts/public/{publicId}).
 */
public record PartDetailDto(Long id, String publicId, PartStatus status, @Nullable String title, @Nullable Integer price, @Nullable PartCondition condition,
                            int quantity, StockStatus stockStatus, @Nullable CategoryDto category, @Nullable String manufacturer,
                            @Nullable String oemNumber, @Nullable PartSide side, @Nullable PartPosition position, List<PhotoDto> photos,
                            List<FitmentDto> fitments, @Nullable FitDto fit, boolean isFavorite, ShopCardDto shop,
                            List<PhotoDto> shopPhotos,
                            @Nullable Instant publishedAt, Instant updatedAt) {

   public record FitDto(String carLabel, boolean fits) {
   }
}
