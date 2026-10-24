package kg.kudaibergen.media;

import java.util.List;

import org.springframework.lang.Nullable;

/**
 * Голосовое (04, 05, 12–14, 16–19): url — файл, durationSec — «0:07», waveform — до 100 пиков 0..1 для полоски
 * (пусто — рисовать ровную), transcript — что сказали, распознаёт сервер (null — не распознано).
 */
public record VoiceDto(Long id, String url, int durationSec, List<Double> waveform, @Nullable String transcript) {
}
