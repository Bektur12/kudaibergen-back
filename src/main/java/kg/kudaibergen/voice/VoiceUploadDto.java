package kg.kudaibergen.voice;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.master.entity.ServiceWhen;
import kg.kudaibergen.media.VoiceDto;
import kg.kudaibergen.request.entity.PartCondition;
import org.springframework.lang.Nullable;

/**
 * Загруженное голосовое и что из него поняли. reply — только для purpose=REPLY (ответ продавца, 14):
 * цена, «Новая / Б/у», «Оригинал / Копия», производитель и чипы. offer — только для purpose=OFFER (отклик
 * мастера, 19): цена «от» и когда. Не понятое — null, продавец или мастер выбирает сам («из голоса» у
 * заполненных полей).
 */
public record VoiceUploadDto(VoiceDto voice, @Nullable ReplyDraftDto reply, @Nullable OfferDraftDto offer) {

   public record ReplyDraftDto(@Nullable Integer price, @Nullable PartCondition condition, @Nullable Boolean original,
                               @Nullable String manufacturer, List<String> tags) {
   }

   /** when — чип «Сейчас / Сегодня / Завтра», availableAt — точное время («Сегодня 15:00»), если сказали. */
   public record OfferDraftDto(@Nullable Integer priceFrom, @Nullable ServiceWhen when, @Nullable Instant availableAt) {
   }
}
