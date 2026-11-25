package kg.kudaibergen.release;

import org.springframework.lang.Nullable;

/**
 * Ответ GET /app/release. updateAvailable — версия ниже той, что в сторе: окно «Доступно обновление» с «Позже»;
 * updateRequired — ниже минимальной: окно без «Позже». storeUrl — кнопка «Обновить».
 */
public record AppReleaseDto(@Nullable String latestVersion, @Nullable String minVersion, @Nullable String storeUrl,
                            boolean updateAvailable, boolean updateRequired) {
}
