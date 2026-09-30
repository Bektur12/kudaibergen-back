package kg.kudaibergen.master.dto;

import java.math.BigDecimal;
import java.util.List;

import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.shop.dto.OpenStateDto;
import org.springframework.lang.Nullable;

/**
 * Профиль мастера для клиента: «СТО «Ходовик» · ★ 4.9 · 212 отзывов», услуги, марки, адрес, радиус, фото.
 * publicId — для ссылки «Поделиться» (GET /masters/public/{publicId}).
 */
public record MasterPublicDto(Long id, String publicId, String name, @Nullable String avatarUrl, BigDecimal rating,
                              int reviewsCount, List<ServiceTypeDto> services, boolean allBrands,
                              List<BrandDto> brands, List<CarOrigin> origins, String address, double lat, double lng,
                              int radiusKm, boolean mobile, OpenStateDto open, @Nullable String phone,
                              List<PhotoDto> photos) {
}
