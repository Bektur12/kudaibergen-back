package kg.kudaibergen.media;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.security.AuthPrincipal;
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
public class MediaController {

   private final MediaService media;

   public MediaController(MediaService media) {
      this.media = media;
   }

   @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @ResponseStatus(HttpStatus.CREATED)
   @Operation(summary = "Загрузить фото (камера 25, «Ещё фото» на 26)", description = """
         JPG или PNG до 10 МБ (телефон заранее сжимает до 1080 px). Сервер пережимает в 1080 и 320 px и убирает
         EXIF. Возвращает id — его передают в mediaIds товара.""")
   public PhotoDto upload(@AuthenticationPrincipal AuthPrincipal principal, @RequestParam MultipartFile file,
                          @RequestParam(defaultValue = "PART") MediaPurpose purpose) {
      return media.uploadPhoto(principal.userId(), purpose, file);
   }
}
