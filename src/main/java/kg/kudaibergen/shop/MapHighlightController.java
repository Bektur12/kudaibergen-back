package kg.kudaibergen.shop;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.market.MarketMapService;
import kg.kudaibergen.market.geo.Point;
import kg.kudaibergen.shop.dto.MapFilterKind;
import kg.kudaibergen.shop.dto.MapHighlightDto;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Подсветка на карте рынка (15). Путь — в разделе карты, данные — магазины, поэтому контроллер в модуле shop. */
@RestController
@RequestMapping("/api/v1/market/map")
@Tag(name = "Карта рынка")
public class MapHighlightController {

   private final MapHighlightService highlight;
   private final MarketMapService market;

   public MapHighlightController(MapHighlightService highlight, MarketMapService market) {
      this.highlight = highlight;
      this.market = market;
   }

   @GetMapping("/highlight")
   @Operation(summary = "Подсветка рядов по марке или категории (15)", description = """
         kind=BRAND&id={brandId} или kind=CATEGORY&id={categoryId}. «Mercedes-Benz — в 6 рядах · 21 бокс ·
         ближайший ряд 14, 170 м»: rowIds, containerIds, shopsCount, rowsCount, openNowCount, nearest*.
         Откуда считать ближайший, как в /market/route: fromX/fromY → fromLat/fromLon → fromEntrance → главный вход.""")
   public MapHighlightDto highlight(@RequestParam MapFilterKind kind, @RequestParam Long id,
                                    @RequestParam(required = false) Double fromX,
                                    @RequestParam(required = false) Double fromY,
                                    @RequestParam(required = false) Double fromLat,
                                    @RequestParam(required = false) Double fromLon,
                                    @RequestParam(required = false) Integer fromEntrance,
                                    @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                          required = false) String language) {
      if ((fromX == null) != (fromY == null)) {
         throw new BadRequestException("BAD_REQUEST", "Передайте fromX и fromY вместе");
      }
      Point from = fromX == null ? null : new Point(fromX, fromY);
      return highlight.highlight(kind, id, market.start(from, fromLat, fromLon, fromEntrance),
            Langs.fromHeader(language));
   }
}
