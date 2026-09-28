package kg.kudaibergen.shop.dto;

import java.math.BigDecimal;
import java.util.List;

import kg.kudaibergen.category.CategoryDto;
import kg.kudaibergen.garage.dto.BrandDto;
import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.shop.entity.MemberRole;
import kg.kudaibergen.shop.entity.ShopStatus;

/**
 * «Мой бокс» (экраны 10, 21, 22). pendingLocation — новое место при переезде до проверки;
 * blockReason — почему магазин скрыт админом; myRole — что можно менять (ТЗ, раздел 2).
 * photos — фото места по порядку, первое — «Обложка» (22, «3 из 8»).
 */
public record MyShopDto(Long id, String name, String avatarUrl, ShopStatus status, String blockReason,
                        MemberRole myRole, LocationDto location, LocationDto pendingLocation,
                        VerificationDto verification, OpenStateDto open, boolean isOpen, String phone,
                        boolean phoneVisible, BigDecimal rating, int reviewsCount, List<BrandDto> brands,
                        List<CategoryDto> categories, int staffCount, List<PhotoDto> photos) {
}
