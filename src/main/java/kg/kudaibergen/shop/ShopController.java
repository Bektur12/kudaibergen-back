package kg.kudaibergen.shop;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.common.i18n.Langs;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.shop.dto.ShopCardDto;
import kg.kudaibergen.shop.dto.ShopPublicDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Магазины глазами покупателя. Чтение открыто гостю, избранное — после входа. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Магазины")
public class ShopController {

   private final ShopService shops;

   public ShopController(ShopService shops) {
      this.shops = shops;
   }

   @GetMapping("/shops")
   @Operation(summary = "Действующие магазины", description = "«Списком» на карте (15), выбор «Боксу» в запросе (06)")
   public CursorPage<ShopCardDto> search(@RequestParam(required = false) Long brandId,
                                         @RequestParam(required = false) Long categoryId,
                                         @RequestParam(required = false) Long rowId,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(required = false) String cursor,
                                         @RequestParam(required = false) Integer limit) {
      return shops.search(brandId, categoryId, rowId, q, cursor, limit);
   }

   @GetMapping("/shops/{id}")
   @Operation(summary = "Профиль продавца для покупателя (30)")
   public ShopPublicDto shop(@PathVariable Long id, @AuthenticationPrincipal AuthPrincipal principal,
                             @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE,
                                   required = false) String language) {
      return shops.publicProfile(id, principal == null ? null : principal.userId(), Langs.fromHeader(language));
   }

   @PutMapping("/shops/{id}/favorite")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "В избранное")
   public void favorite(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      shops.addFavorite(principal.userId(), id);
   }

   @DeleteMapping("/shops/{id}/favorite")
   @ResponseStatus(HttpStatus.NO_CONTENT)
   @Operation(summary = "Убрать из избранного")
   public void unfavorite(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      shops.removeFavorite(principal.userId(), id);
   }

   @GetMapping("/me/favorite-shops")
   @Operation(summary = "Избранные магазины (19)")
   public List<ShopCardDto> favorites(@AuthenticationPrincipal AuthPrincipal principal) {
      return shops.favorites(principal.userId());
   }
}
