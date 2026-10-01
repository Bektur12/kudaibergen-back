package kg.kudaibergen.ocr;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.security.AuthPrincipal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ocr")
@Tag(name = "Каталог")
public class OcrController {

   private final OcrService ocr;

   public OcrController(OcrService ocr) {
      this.ocr = ocr;
   }

   @PostMapping(value = "/oem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
   @Operation(summary = "Распознать номер детали на фото", description = """
         Камера 25 в режиме «Номер детали» (подставить в oemNumber) и камера в строке поиска 27 (искать по номеру:
         GET /parts/search?q=<oem>). Кандидаты — от самого вероятного, partsFound — сколько таких на рынке.""")
   @ApiResponse(responseCode = "503", description = "OCR_UNAVAILABLE — распознавание не настроено или недоступно")
   @ApiResponse(responseCode = "429", description = "OCR_LIMIT")
   public OemRecognitionDto oem(@AuthenticationPrincipal AuthPrincipal principal, @RequestParam MultipartFile file) {
      return ocr.recognizeOem(principal.userId(), file);
   }
}
