package kg.kudaibergen.voice;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kg.kudaibergen.common.error.RateLimitException;
import kg.kudaibergen.common.ratelimit.RateLimiter;
import kg.kudaibergen.garage.VehicleDirectory;
import kg.kudaibergen.garage.entity.Brand;
import kg.kudaibergen.garage.entity.CarModel;
import kg.kudaibergen.media.MediaItemDto;
import kg.kudaibergen.media.MediaPurpose;
import kg.kudaibergen.media.MediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/**
 * «Выложить видео»: видео товара сохраняется как обычное (purpose PART), звук распознаётся тем же сервисом,
 * что голосовые, и разбирается в поля формы. Распознавание не обязательно: выключено, сверх лимита или файл
 * больше, чем принимает сервис, — видео сохраняется, поля продавец заполняет сам.
 */
@Service
public class PartVideoService {

   /** Столько принимает /v1/audio/transcriptions у OpenAI. */
   static final DataSize MAX_TRANSCRIBE_SIZE = DataSize.ofMegabytes(25);
   private static final Logger log = LoggerFactory.getLogger(PartVideoService.class);

   private final MediaService media;
   private final SpeechToText speech;
   private final RateLimiter rateLimiter;
   private final VoiceProperties properties;
   private final VehicleDirectory directory;

   public PartVideoService(MediaService media, SpeechToText speech, RateLimiter rateLimiter,
                           VoiceProperties properties, VehicleDirectory directory) {
      this.media = media;
      this.speech = speech;
      this.rateLimiter = rateLimiter;
      this.properties = properties;
      this.directory = directory;
   }

   public PartVideoUploadDto upload(Long userId, MultipartFile file, Integer durationSec, MultipartFile poster) {
      MediaItemDto video = media.uploadVideo(userId, MediaPurpose.PART, file, durationSec, poster);
      String transcript = transcribe(userId, file);
      VoiceParser.PartDraft draft = VoiceParser.part(transcript, catalog());
      return new PartVideoUploadDto(video, transcript, new PartVideoUploadDto.PartVideoDraftDto(draft.title(), draft.price(),
            draft.condition(), draft.models().stream()
                  .map(model -> new PartVideoUploadDto.VideoModelDto(model.modelId(), model.brandId(), model.label()))
                  .toList()));
   }

   private String transcribe(Long userId, MultipartFile file) {
      if (file.getSize() > MAX_TRANSCRIBE_SIZE.toBytes()) {
         return null;
      }
      try {
         rateLimiter.hit("voice:" + userId, properties.perUserHourly(), Duration.ofHours(1), "VOICE_LIMIT",
               "Слишком много голосовых — попробуйте позже");
      } catch (RateLimitException limited) {
         return null;
      }
      String text;
      try {
         String name = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
               ? "video.mp4" : file.getOriginalFilename();
         text = speech.transcribe(file.getBytes(), file.getContentType(), name);
      } catch (IOException | RuntimeException e) {
         log.warn("Звук видео не распознан: {}", e.getMessage());
         return null;
      }
      if (text == null || text.isBlank()) {
         return null;
      }
      String trimmed = text.strip();
      return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
   }

   /** Все модели справочника — их называют вслух: «камри пятьдесят». */
   private List<VoiceParser.ModelOption> catalog() {
      List<VoiceParser.ModelOption> options = new ArrayList<>();
      for (Brand brand : directory.brands()) {
         for (CarModel model : directory.modelsOf(brand.getId())) {
            options.add(new VoiceParser.ModelOption(model.getId(), brand.getId(), model.label(),
                  model.getName().toLowerCase(Locale.ROOT), model.getGeneration(),
                  model.getAliases() == null ? List.of() : model.getAliases()));
         }
      }
      return options;
   }
}
