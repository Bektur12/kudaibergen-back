package kg.kudaibergen.legal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Публичные страницы для App Store и Google Play: /privacy, /terms, /support, /delete-account — без входа.
 * HTML лежит в resources/legal; {{operator}}, {{email}}, {{phone}}, {{updated}} подставляются из app.legal.
 */
@Hidden
@RestController
public class LegalPagesController {

   private static final Map<String, String> PAGES = Map.of(
         "privacy", "legal/privacy.html",
         "terms", "legal/terms.html",
         "support", "legal/support.html",
         "delete-account", "legal/delete-account.html");

   private final LegalProperties legal;

   public LegalPagesController(LegalProperties legal) {
      this.legal = legal;
   }

   @GetMapping(value = "/{page:privacy|terms|support|delete-account}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
   public String page(@PathVariable String page) throws IOException {
      try (InputStream in = new ClassPathResource(PAGES.get(page)).getInputStream()) {
         return new String(in.readAllBytes(), StandardCharsets.UTF_8)
               .replace("{{operator}}", escape(legal.operator()))
               .replace("{{email}}", escape(legal.email()))
               .replace("{{phone}}", escape(legal.phone()))
               .replace("{{updated}}", escape(legal.updated()));
      }
   }

   private static String escape(String value) {
      return value == null ? "" : HtmlUtils.htmlEscape(value);
   }
}
