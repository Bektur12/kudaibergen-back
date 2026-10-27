package kg.kudaibergen.master.dto;

import java.math.BigDecimal;

import org.springframework.lang.Nullable;

/**
 * Мастер кратко: отклик на заявку (37), шапка чата, списки. isOpenNow — «● Принимаю». lat/lng — точка мастера
 * для «Маршрут» (2GIS).
 */
public record MasterCardDto(Long id, String name, @Nullable String avatarUrl, BigDecimal rating, int reviewsCount,
                            String address, boolean mobile, boolean isOpenNow, @Nullable String phone, double lat,
                            double lng) {
}
