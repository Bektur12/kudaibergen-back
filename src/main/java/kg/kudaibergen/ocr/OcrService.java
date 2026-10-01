package kg.kudaibergen.ocr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.common.config.AppProperties;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.ratelimit.RateLimiter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Распознавание номера детали: фото → текст (TextRecognizer) → кандидаты (OemExtractor) → есть ли на рынке. */
@Service
public class OcrService {

   private final TextRecognizer recognizer;
   private final RateLimiter rateLimiter;
   private final NamedParameterJdbcTemplate jdbc;
   private final OcrProperties properties;
   private final long maxBytes;

   public OcrService(TextRecognizer recognizer, RateLimiter rateLimiter, NamedParameterJdbcTemplate jdbc,
                     OcrProperties properties, AppProperties app) {
      this.recognizer = recognizer;
      this.rateLimiter = rateLimiter;
      this.jdbc = jdbc;
      this.properties = properties;
      this.maxBytes = app.media().maxPhotoSize().toBytes();
   }

   public OemRecognitionDto recognizeOem(Long userId, MultipartFile file) {
      if (file == null || file.isEmpty()) {
         throw new BadRequestException("FILE_REQUIRED", "Файл не передан");
      }
      if (file.getContentType() == null || !file.getContentType().startsWith("image/")) {
         throw new BadRequestException("BAD_MEDIA_TYPE", "Ожидалось изображение");
      }
      if (file.getSize() > maxBytes) {
         throw new BadRequestException("FILE_TOO_LARGE", "Фото слишком большое");
      }
      rateLimiter.hit("ocr:" + userId, properties.perUserHourly(), Duration.ofHours(1), "OCR_LIMIT",
            "Слишком много распознаваний — попробуйте позже");
      byte[] bytes;
      try {
         bytes = file.getBytes();
      } catch (IOException e) {
         throw new UncheckedIOException("Не удалось прочитать файл", e);
      }
      List<OemExtractor.Candidate> candidates = OemExtractor.extract(recognizer.recognize(bytes));
      return new OemRecognitionDto(candidates.stream()
            .map(candidate -> new OemRecognitionDto.CandidateDto(candidate.display(), candidate.normalized(),
                  partsWithOem(candidate.normalized())))
            .toList());
   }

   private long partsWithOem(String normalized) {
      Long count = jdbc.queryForObject("""
            select count(*) from parts p join shops s on s.id = p.shop_id
            where p.oem_norm = :oem and p.status = 'ACTIVE' and s.status = 'ACTIVE'""",
            Map.of("oem", normalized), Long.class);
      return count == null ? 0 : count;
   }
}
