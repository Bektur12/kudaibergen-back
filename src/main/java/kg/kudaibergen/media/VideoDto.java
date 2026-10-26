package kg.kudaibergen.media;

import org.springframework.lang.Nullable;

/**
 * Видео товара в ленте и на карточке: url — файл, posterUrl — обложка 1080 px (null — клиент показывает первый
 * кадр), durationSec — «▶ 0:15».
 */
public record VideoDto(Long id, String url, @Nullable String posterUrl, int durationSec) {
}
