package kg.kudaibergen.catalog.dto;

import java.util.List;

import kg.kudaibergen.media.VideoDto;
import kg.kudaibergen.request.entity.PartCondition;
import org.springframework.lang.Nullable;

/**
 * Видео в ленте «Рынок» и в «Видео с рынка» на главной. fits — подходит выбранной машине (null — машина не
 * выбрана), fitsLabel — «Подходят на Camry 50 и 55 · 2011–2017», shipsToRegions — чип «Доставка по КР» и кнопка
 * «Доставка». commentsCount — счётчик у кнопки «Комм.».
 */
public record VideoFeedItemDto(Long partId, String publicId, String title, int price, String currency,
                               PartCondition condition, VideoDto video, @Nullable Boolean fits,
                               @Nullable String fitsLabel, boolean shipsToRegions, boolean isFavorite,
                               int commentsCount, VideoShopDto shop) {

   /**
    * Продавец под видео: «Автодеталь Азамат», place — «Ряд 14 · конт. 12», phone — для «Позвонить» (null — скрыт),
    * replyMinutes — «отвечает за 3 мин» (среднее за 30 дней, null — ответов не было).
    */
   public record VideoShopDto(Long id, String name, @Nullable String avatarUrl, String place, @Nullable String phone,
                              @Nullable Integer replyMinutes) {
   }

   /** Страница ленты: nextCursor = null — дальше ничего. */
   public record VideoFeedPageDto(List<VideoFeedItemDto> items, @Nullable String nextCursor) {
   }
}
