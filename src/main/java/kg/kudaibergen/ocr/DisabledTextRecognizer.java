package kg.kudaibergen.ocr;

import kg.kudaibergen.common.error.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** OCR не настроен: клиент прячет режим «Номер детали» и камеру в строке поиска. */
@Component
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = "none", matchIfMissing = true)
public class DisabledTextRecognizer implements TextRecognizer {

   @Override
   public String recognize(byte[] image) {
      throw new ApiException("OCR_UNAVAILABLE", "Распознавание номера пока недоступно — введите его вручную",
            HttpStatus.SERVICE_UNAVAILABLE);
   }
}
