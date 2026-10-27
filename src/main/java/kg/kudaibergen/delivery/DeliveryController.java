package kg.kudaibergen.delivery;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kg.kudaibergen.common.idempotency.Idempotent;
import kg.kudaibergen.common.security.AuthPrincipal;
import kg.kudaibergen.delivery.dto.DeliveryInputs;
import kg.kudaibergen.delivery.dto.DeliveryOrderDto;
import kg.kudaibergen.delivery.dto.DeliveryTariffsDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Доставка Ылдам Экспресс: тарифы, заказ с видео товара, «Где посылка» и шаги продавца. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Доставка")
public class DeliveryController {

   private final DeliveryService delivery;

   public DeliveryController(DeliveryService delivery) {
      this.delivery = delivery;
   }

   @GetMapping("/delivery/tariffs")
   @Operation(summary = "Тарифы «КУДА ВЕЗТИ»", description = """
         Плитки областей (Бишкек — сегодня, области — 1 день, Баткен — 2 дня), у Оша и Джалал-Абада — чипы
         отдалённых районов (2 дня). Цена — до 5 кг; доплату за вес и размер считает курьер.""")
   public DeliveryTariffsDto tariffs() {
      return delivery.tariffs();
   }

   @PostMapping("/delivery-orders")
   @ResponseStatus(HttpStatus.CREATED)
   @Idempotent
   @Operation(summary = "Заказать доставку", description = """
         Товар должен быть в продаже и с «Отправлю в регионы». Продавцу — пуш и карточка заказа в чате
         покупателя с боксом (chatId в ответе). Оплату товара договариваются в чате.""")
   @ApiResponse(responseCode = "400", description = "DELIVERY_NOT_AVAILABLE, SELF_ORDER, BAD_DISTRICT, BAD_PHONE")
   public DeliveryOrderDto create(@AuthenticationPrincipal AuthPrincipal principal,
                                  @Valid @RequestBody DeliveryInputs.CreateOrder input) {
      return delivery.create(principal.userId(), input);
   }

   @GetMapping("/delivery-orders")
   @Operation(summary = "Мои заказы с доставкой", description = """
         side = BUYER — мои как покупателя («Посылка KDB-… · В пути» на главной), SHOP — заказы моего бокса
         (сверху «Запросов» продавца). active = true — без полученных и отменённых. До 50, новые сверху.""")
   public List<DeliveryOrderDto> list(@AuthenticationPrincipal AuthPrincipal principal,
                                      @RequestParam(defaultValue = "BUYER") DeliveryService.DeliverySide side,
                                      @RequestParam(defaultValue = "true") boolean active) {
      return delivery.list(principal.userId(), side, active);
   }

   @GetMapping("/delivery-orders/{id}")
   @Operation(summary = "«Где посылка» (покупатель) или «Заказ с доставкой» (продавец)",
         description = "buyer — только продавцу")
   public DeliveryOrderDto get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return delivery.get(principal.userId(), id);
   }

   @PostMapping("/delivery-orders/{id}/received")
   @Operation(summary = "«Получил» — покупатель", description = "После того как заказан курьер или он забрал посылку")
   @ApiResponse(responseCode = "409", description = "BAD_STATUS")
   public DeliveryOrderDto received(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
      return delivery.received(principal.userId(), id);
   }

   @PostMapping("/delivery-orders/{id}/packed")
   @Operation(summary = "«Упаковал — жду курьера» — продавец", description = """
         Вес (кг) и «Хрупкое» уходят в заявку Ылдам. До 17:30 — курьер заберёт сегодня, позже — завтра;
         курьера заказывает администрация.""")
   @ApiResponse(responseCode = "409", description = "BAD_STATUS")
   public DeliveryOrderDto packed(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id,
                                  @Valid @RequestBody DeliveryInputs.Packed input) {
      return delivery.packed(principal.userId(), id, input);
   }

   @PostMapping("/delivery-orders/{id}/picked-up")
   @Operation(summary = "«Курьер забрал» — продавец", description = "Покупателю — пуш «Курьер забрал посылку»")
   @ApiResponse(responseCode = "409", description = "BAD_STATUS")
   public DeliveryOrderDto pickedUp(@AuthenticationPrincipal AuthPrincipal principal,
                                    @Parameter(description = "id заказа") @PathVariable Long id) {
      return delivery.pickedUp(principal.userId(), id);
   }
}
