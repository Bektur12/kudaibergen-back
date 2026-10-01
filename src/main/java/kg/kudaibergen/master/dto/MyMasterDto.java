package kg.kudaibergen.master.dto;

import java.math.BigDecimal;
import java.util.List;

import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.shop.dto.OpenStateDto;
import kg.kudaibergen.shop.entity.ShopStatus;
import org.springframework.lang.Nullable;

/** Свой профиль мастера (38): всё для редактирования и шапки вкладки «Профиль». */
public record MyMasterDto(Long id, String publicId, String name, @Nullable String avatarUrl, ShopStatus status,
                          @Nullable String blockReason, List<ServiceTypeDto> services, boolean allBrands,
                          List<BrandDto> brands, List<CarOrigin> origins, String address, double lat, double lng,
                          int radiusKm, boolean mobile, boolean accepting, OpenStateDto open, @Nullable String phone,
                          BigDecimal rating, int reviewsCount, List<PhotoDto> photos) {
}
