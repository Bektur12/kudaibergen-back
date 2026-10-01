package kg.kudaibergen.admin.dictionaries;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.media.MediaPurpose;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.PhotoDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Загрузка картинок из админки (токен приложения сюда не нужен): пока — логотипы марок. */
@RestController
@RequestMapping("/api/v1/admin/media")
@Tag(name = "Админка: справочники")
public class AdminMediaController {

   private final MediaService media;

   public AdminMediaController(MediaService media) {
      this.media = media;
   }

   @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @ResponseStatus(HttpStatus.CREATED)
   @PreAuthorize("hasAuthority('DICTIONARIES_EDIT')")
   @Audited(action = "MEDIA_UPLOAD", entity = "MEDIA")
   @Operation(summary = "Загрузить логотип марки", description = "JPG или PNG до 10 МБ; id передаётся в "
         + "PUT /admin/dictionaries/brands/{id}/logo. Пережимается в JPEG — прозрачный фон станет белым")
   public PhotoDto upload(@AuthenticationPrincipal AuthPrincipal admin, @RequestParam MultipartFile file) {
      PhotoDto photo = media.uploadPhoto(admin.userId(), MediaPurpose.BRAND, file);
      AuditTrail.entityId(photo.id());
      return photo;
   }
}
