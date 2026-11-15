package kg.kudaibergen.master;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.garage.entity.CarOrigin;
import kg.kudaibergen.master.dto.NearbyMasterDto;
import kg.kudaibergen.master.dto.OfflinePackDto;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Помощь на дороге: сломался — сразу видно ближайших мастеров, СТО и эвакуаторы и им можно позвонить; справочник
 * на телефон — для мест без интернета (перевалы на трассе Бишкек–Ош). Только после входа.
 */
@RestController
@RequestMapping("/api/v1/roadside")
@Tag(name = "Помощь на дороге")
public class RoadsideController {

   private final RoadsideService roadside;

   public RoadsideController(RoadsideService roadside) {
      this.roadside = roadside;
   }

   @GetMapping("/nearby")
   @Operation(summary = "Кто рядом — без заявки", description = """
         Ближайшие действующие мастера (service — только этой услуги, например TOW_TRUCK), без обрезки по радиусу:
         на трассе ближайший эвакуатор может быть за 100 км. Сначала те, кто доедет (reachesYou) и берётся за машину
         (worksWithCar; машина — carId из гаража или brandId + origin, необязательно), дальше — по расстоянию.
         У каждого phone — «Позвонить», lat/lng — «Маршрут». limit — по умолчанию 20, до 50.""")
   public List<NearbyMasterDto> nearby(@AuthenticationPrincipal AuthPrincipal principal,
                                       @RequestParam double lat, @RequestParam double lng,
                                       @RequestParam(required = false) String service,
                                       @RequestParam(required = false) Long carId,
                                       @RequestParam(required = false) Long brandId,
                                       @RequestParam(required = false) CarOrigin origin,
                                       @RequestParam(required = false) Integer limit) {
      return roadside.nearby(principal.userId(), lat, lng, service, carId, brandId, origin, limit);
   }

   @GetMapping("/offline-pack")
   @Operation(summary = "Справочник мастеров на телефон (без интернета)", description = """
         Все действующие мастера, СТО и эвакуаторы: имя, телефон, точка, услуги, радиус, марки — без фото.
         Плюс услуги для чипсов и экстренные номера (112, 103, 102, 101). Хранить на телефоне: без сети расстояние
         считать по GPS (гаверсинус), «Позвонить» — tel:, «SMS» с координатами — sms:.
         Обновлять после входа и раз в сутки с If-None-Match: без изменений — 304 без тела (не тратит лимит).""")
   @ApiResponse(responseCode = "304", description = "Справочник не менялся — оставить сохранённый")
   @ApiResponse(responseCode = "429", description = "PACK_RATE_LIMITED — больше 20 скачиваний за сутки")
   public ResponseEntity<OfflinePackDto> pack(@AuthenticationPrincipal AuthPrincipal principal, WebRequest request) {
      OfflinePackDto pack = roadside.pack();
      if (request.checkNotModified(etag(pack.version()))) {
         return null;
      }
      roadside.countDownload(principal.userId());
      return ResponseEntity.ok().eTag(etag(pack.version())).cacheControl(CacheControl.noCache().cachePrivate())
            .body(pack);
   }

   @GetMapping("/offline-pack/version")
   @Operation(summary = "Версия справочника", description = """
         Короткая проверка при запуске: version не совпала с сохранённой — скачать /roadside/offline-pack.""")
   public OfflinePackDto.Version version() {
      return roadside.version();
   }

   private static String etag(String version) {
      return "\"" + version + "\"";
   }
}
