package kg.kudaibergen.market;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.market.dto.LocateDto;
import kg.kudaibergen.market.dto.LocateRequest;
import kg.kudaibergen.market.dto.MapDto;
import kg.kudaibergen.market.dto.MarketSearchDto;
import kg.kudaibergen.market.dto.QrResolveDto;
import kg.kudaibergen.market.dto.RouteDto;
import kg.kudaibergen.market.dto.RowDetailDto;
import kg.kudaibergen.market.dto.RowDto;
import kg.kudaibergen.market.geo.Point;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Карта рынка (экраны 10а, 15, 18). Всё открыто гостю. */
@RestController
@RequestMapping("/api/v1/market")
@Tag(name = "Карта рынка")
public class MarketController {

   private final MarketMapService market;

   public MarketController(MarketMapService market) {
      this.market = market;
   }

   @GetMapping("/map")
   @Operation(summary = "Схема рынка целиком (15, 18)", description = """
         Граница, улицы, ряды с контейнерами, проходы, входы, парковка, матрица GPS.
         ETag "map-v{version}": с If-None-Match приходит 304, если схема не менялась.""")
   public ResponseEntity<MapDto> map(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String etag) {
      String current = "\"map-v" + market.currentVersion() + "\"";
      if (current.equals(etag)) {
         return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(current).build();
      }
      return ResponseEntity.ok().eTag(current).cacheControl(CacheControl.noCache()).body(market.map());
   }

   @GetMapping("/rows")
   @Operation(summary = "Ряды в порядке сетки (10а)")
   public List<RowDto> rows() {
      return market.rows();
   }

   @GetMapping("/rows/{id}")
   @Operation(summary = "Контейнеры ряда по сторонам",
         description = "occupied — место занято магазином (серое на 10а); shop = null — «Нет продавца в приложении»")
   public RowDetailDto row(@PathVariable Long id) {
      return market.row(id);
   }

   @GetMapping("/search")
   @Operation(summary = "Поиск «Ряд, бокс или магазин»", description = "«14», «ряд ю», «14 12», «жайма 3 5»")
   public MarketSearchDto search(@RequestParam String q) {
      return market.search(q);
   }

   @PostMapping("/locate")
   @Operation(summary = "GPS → точка на схеме", description = "Координаты не сохраняются")
   @ApiResponse(responseCode = "409", description = "MAP_NOT_CALIBRATED — опорные точки ещё не заданы")
   public LocateDto locate(@Valid @RequestBody LocateRequest request) {
      return market.locate(request);
   }

   @GetMapping("/qr/{token}")
   @Operation(summary = "QR-табличка ряда или наклейка контейнера → точка на схеме")
   public QrResolveDto qr(@PathVariable String token) {
      return market.resolveQr(token);
   }

   @GetMapping("/route")
   @Operation(summary = "Как пройти к боксу (18)", description = """
         Откуда: fromX/fromY (точка схемы) → fromLat/fromLon (GPS) → fromEntrance (индекс входа).
         Без всего этого — от главного входа. При отклонении больше 20 м клиент просто запрашивает маршрут заново.
         Язык шагов — Accept-Language (ru / ky).""")
   public RouteDto route(@RequestParam Long toContainerId,
                         @RequestParam(required = false) Double fromX,
                         @RequestParam(required = false) Double fromY,
                         @RequestParam(required = false) Double fromLat,
                         @RequestParam(required = false) Double fromLon,
                         @RequestParam(required = false) Integer fromEntrance,
                         @Parameter(hidden = true)
                         @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String language) {
      if ((fromX == null) != (fromY == null)) {
         throw new BadRequestException("BAD_REQUEST", "Передайте fromX и fromY вместе");
      }
      Point from = fromX == null ? null : new Point(fromX, fromY);
      return market.route(toContainerId, from, fromLat, fromLon, fromEntrance, Langs.fromHeader(language));
   }
}
