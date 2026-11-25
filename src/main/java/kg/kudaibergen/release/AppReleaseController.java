package kg.kudaibergen.release;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
@Tag(name = "Версия приложения")
public class AppReleaseController {

   private final AppReleaseService releases;

   public AppReleaseController(AppReleaseService releases) {
      this.releases = releases;
   }

   @GetMapping("/release")
   @Operation(summary = "Есть ли обновление", description = """
         При запуске и при возврате в приложение: version — версия приложения (1.0.5). Гостю тоже.""")
   public AppReleaseDto release(@RequestParam AppReleaseService.StorePlatform platform, @RequestParam String version) {
      return releases.check(platform, version);
   }
}
