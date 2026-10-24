package kg.kudaibergen.voice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.media.MediaPurpose;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
@Tag(name = "Фото")
public class VoiceController {

   private final VoiceService voices;

   public VoiceController(VoiceService voices) {
      this.voices = voices;
   }

   @PostMapping(value = "/voices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Загрузить голосовое (04, 14, 16, 19)", description = """
         Аудио до 60 секунд и 15 МБ (M4A, AAC, MP3, OGG, WAV, WebM, AMR). purpose: REQUEST — запрос покупателя,
         REPLY — ответ продавца, SERVICE — заявка мастеру, OFFER — отклик мастера. Сервер распознаёт речь
         (voice.transcript) и для REPLY / OFFER разбирает цену, состояние и время (reply / offer) — ими
         заполняется экран «Проверьте и отправьте». id передаётся в voiceMediaId запроса, ответа, заявки или отклика.""")
   @ApiResponse(responseCode = "400", description = "VOICE_TOO_LONG, DURATION_REQUIRED, BAD_MEDIA_TYPE, "
         + "FILE_TOO_LARGE, VOICE_NOT_ALLOWED, BAD_WAVEFORM")
   public VoiceUploadDto upload(@AuthenticationPrincipal AuthPrincipal principal, @RequestParam MultipartFile file,
                                @Parameter(description = "Длина в секундах (у M4A сервер читает сам)")
                                @RequestParam(required = false) Integer durationSec,
                                @Parameter(description = "JSON-массив до 100 пиков громкости 0..1")
                                @RequestParam(required = false) String waveform,
                                @RequestParam MediaPurpose purpose) {
      return voices.upload(principal.userId(), purpose, file, durationSec, waveform);
   }
}
