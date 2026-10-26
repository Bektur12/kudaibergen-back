package kg.kudaibergen.voice;

import java.util.List;

import kg.kudaibergen.media.MediaItemDto;
import kg.kudaibergen.request.entity.PartCondition;
import org.springframework.lang.Nullable;

/**
 * Видео товара («Выложить видео») и что продавец сказал в нём: transcript — распознанный текст (null — не
 * распознано), draft — поля формы «из голоса»: название, цена, состояние и машины (чипы «Camry 50»).
 * Пустой draft — продавец заполняет сам.
 */
public record PartVideoUploadDto(MediaItemDto video, @Nullable String transcript, PartVideoDraftDto draft) {

   public record PartVideoDraftDto(@Nullable String title, @Nullable Integer price, @Nullable PartCondition condition,
                          List<VideoModelDto> models) {
   }

   /** Машина для «На какие машины»: в fitments уходит brandId + modelId. */
   public record VideoModelDto(Long modelId, Long brandId, String label) {
   }
}
