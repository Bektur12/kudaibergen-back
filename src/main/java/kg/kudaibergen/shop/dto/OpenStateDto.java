package kg.kudaibergen.shop.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * «● Открыто до 17:00» / «Закрыто · Откроется в 08:00».
 * closedManually — продавец включил «Бокс закрыт»; opensAt — только когда сейчас закрыто.
 */
public record OpenStateDto(boolean openNow, boolean closedManually, LocalTime openFrom, LocalTime openTo,
                           Set<DayOfWeek> workDays, @Nullable OffsetDateTime opensAt) {
}
