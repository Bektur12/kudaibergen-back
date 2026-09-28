package kg.kudaibergen.shop.dto;

import kg.kudaibergen.market.dto.LocationDto;
import kg.kudaibergen.shop.entity.VerificationMethod;
import kg.kudaibergen.shop.entity.VerificationStatus;

/**
 * Проверка места (экраны «Сканируйте QR на контейнере» и «Магазин на проверке»).
 * required = false — подтверждать нечего. last* — последняя попытка (например, отказ админа с причиной).
 * smsAvailable — у контейнера есть номер арендатора из базы рынка.
 */
public record VerificationDto(boolean required, LocationDto target, boolean smsAvailable, String smsPhoneMasked,
                              VerificationStatus lastStatus, VerificationMethod lastMethod, String lastReason,
                              boolean waitingForAdmin) {
}
