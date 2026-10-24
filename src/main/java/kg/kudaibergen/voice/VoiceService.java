package kg.kudaibergen.voice;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kg.kudaibergen.common.config.ClockConfig;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.RateLimitException;
import kg.kudaibergen.common.ratelimit.RateLimiter;
import kg.kudaibergen.media.Media;
import kg.kudaibergen.media.MediaPurpose;
import kg.kudaibergen.media.MediaService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Голосовое к запросу, ответу, заявке или отклику: проверка, распознавание (вне транзакции — сервис может
 * отвечать несколько секунд), запись и разбор цены и времени для ответа продавца и отклика мастера.
 */
@Service
public class VoiceService {

   static final int MAX_WAVEFORM_POINTS = 100;

   private final MediaService media;
   private final SpeechToText speech;
   private final RateLimiter rateLimiter;
   private final VoiceProperties properties;
   private final ObjectMapper json;
   private final Clock clock;

   public VoiceService(MediaService media, SpeechToText speech, RateLimiter rateLimiter, VoiceProperties properties,
                       ObjectMapper json, Clock clock) {
      this.media = media;
      this.speech = speech;
      this.rateLimiter = rateLimiter;
      this.properties = properties;
      this.json = json;
      this.clock = clock;
   }

   public VoiceUploadDto upload(Long userId, MediaPurpose purpose, MultipartFile file, Integer durationSec,
                                String waveformJson) {
      MediaService.VoiceFile voice = media.checkVoice(purpose, file, durationSec);
      List<Double> waveform = waveform(waveformJson);
      String transcript = transcribe(userId, voice);
      Media saved = media.storeVoice(userId, purpose, voice, waveform, transcript);
      VoiceUploadDto.ReplyDraftDto reply = null;
      VoiceUploadDto.OfferDraftDto offer = null;
      if (purpose == MediaPurpose.REPLY) {
         VoiceParser.ReplyDraft draft = VoiceParser.reply(transcript);
         reply = new VoiceUploadDto.ReplyDraftDto(draft.price(), draft.condition(), draft.original(),
               draft.manufacturer(), draft.tags());
      } else if (purpose == MediaPurpose.OFFER) {
         VoiceParser.OfferDraft draft = VoiceParser.offer(transcript,
               ZonedDateTime.now(clock.withZone(ClockConfig.MARKET_ZONE)));
         offer = new VoiceUploadDto.OfferDraftDto(draft.priceFrom(), draft.when(), draft.availableAt());
      }
      return new VoiceUploadDto(media.voice(saved), reply, offer);
   }

   /** Сверх часового лимита — без текста: голосовое важнее, поля заполнят руками. */
   private String transcribe(Long userId, MediaService.VoiceFile voice) {
      try {
         rateLimiter.hit("voice:" + userId, properties.perUserHourly(), Duration.ofHours(1), "VOICE_LIMIT",
               "Слишком много голосовых — попробуйте позже");
      } catch (RateLimitException limited) {
         return null;
      }
      String text = speech.transcribe(voice.bytes(), voice.mimeType(), "voice" + voice.extension());
      if (text == null) {
         return null;
      }
      String trimmed = text.strip();
      return trimmed.isEmpty() ? null : trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
   }

   /** JSON-массив до 100 пиков громкости 0..1; пусто — без волны. */
   private List<Double> waveform(String raw) {
      if (raw == null || raw.isBlank()) {
         return null;
      }
      List<Double> peaks;
      try {
         peaks = json.readValue(raw, new TypeReference<List<Double>>() {
         });
      } catch (Exception e) {
         throw new BadRequestException("BAD_WAVEFORM", "waveform — JSON-массив чисел");
      }
      if (peaks.isEmpty() || peaks.size() > MAX_WAVEFORM_POINTS
            || peaks.stream().anyMatch(peak -> peak == null || peak.isNaN() || peak < 0 || peak > 1)) {
         throw new BadRequestException("BAD_WAVEFORM", "waveform — от 1 до 100 чисел от 0 до 1");
      }
      return new ArrayList<>(peaks);
   }
}
