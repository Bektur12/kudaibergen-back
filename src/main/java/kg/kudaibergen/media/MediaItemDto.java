package kg.kudaibergen.media;

import org.springframework.lang.Nullable;

/**
 * Вложение — фото или видео. Фото: url — 1080 px, thumbUrl — 320 px. Видео: url — сам файл,
 * thumbUrl / width / height — обложка (null — обложки нет, клиент показывает первый кадр), durationSec — длина.
 */
public record MediaItemDto(Long id, MediaKind kind, String url, @Nullable String thumbUrl, @Nullable Integer width,
                           @Nullable Integer height, @Nullable Integer durationSec) {
}
