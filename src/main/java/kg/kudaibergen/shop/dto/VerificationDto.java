package kg.kudaibergen.shop.dto;

import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.shop.entity.VerificationMethod;
import kg.kudaibergen.shop.entity.VerificationStatus;
import org.springframework.lang.Nullable;

/**
 * Проверка места (экраны «Сканируйте QR на контейнере» и «Магазин на проверке»).
 * required = false — подтверждать нечего. last* — последняя попытка (например, отказ админа с причиной).
 * smsAvailable — у контейнера есть номер арендатора из базы рынка.
 */
public record VerificationDto(boolean required, @Nullable LocationDto target, boolean smsAvailable, @Nullable String smsPhoneMasked,
                              @Nullable VerificationStatus lastStatus, @Nullable VerificationMethod lastMethod, @Nullable String lastReason,
                              boolean waitingForAdmin) {
}
