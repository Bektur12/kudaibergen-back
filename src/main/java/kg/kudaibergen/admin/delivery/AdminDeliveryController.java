package kg.kudaibergen.admin.delivery;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import kg.kudaibergen.admin.audit.AuditTrail;
import kg.kudaibergen.admin.audit.Audited;
import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.delivery.entity.DeliveryStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Доставки Ылдам Экспресс: заказ курьера по WhatsApp из заявки, заполненной из заказа. */
@RestController
@RequestMapping("/api/v1/admin/delivery-orders")
@Tag(name = "Админка: доставки")
public class AdminDeliveryController {

   private final AdminDeliveryService delivery;

   public AdminDeliveryController(AdminDeliveryService delivery) {
      this.delivery = delivery;
   }

   @GetMapping
   @PreAuthorize("hasAuthority('REQUESTS_VIEW')")
   @Operation(summary = "Заказы с доставкой", description = "status — таб; q — номер, товар или цифры телефона. "
         + "counts — {all, NEW, PACKED, COURIER_ORDERED, PICKED_UP, DELIVERED, CANCELLED} с учётом q. Живые "
         + "изменения — admin:requests {type: DELIVERY_CHANGED, payload: {id, number, event, sellerName, at}}")
   public AdminPage<AdminDeliveryOrderDto, Map<String, Long>> list(
         @RequestParam(required = false) DeliveryStatus status,
         @RequestParam(required = false) String q,
         @RequestParam(required = false) String cursor,
         @RequestParam(required = false) Integer limit) {
      return delivery.list(status, q, cursor, limit);
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('REQUESTS_VIEW')")
   @Operation(summary = "«Заказать курьера»: заказ, заявка Ылдам (template) и ссылка WhatsApp")
   public AdminDeliveryOrderDto get(@PathVariable Long id) {
      return delivery.get(id);
   }

   @PostMapping("/{id}/courier-ordered")
   @PreAuthorize("hasAuthority('REQUESTS_MANAGE')")
   @Audited(action = "DELIVERY_COURIER_ORDERED", entity = "DELIVERY_ORDER", id = "#id")
   @Operation(summary = "Заявка отправлена в WhatsApp Ылдам", description = "Из PACKED. Покупателю — пуш "
         + "«Заказали курьера Ылдам»")
   @ApiResponse(responseCode = "409", description = "BAD_STATUS")
   public AdminDeliveryOrderDto courierOrdered(@PathVariable Long id) {
      AuditTrail.before(delivery.get(id));
      return delivery.courierOrdered(id);
   }

   @PostMapping("/{id}/delivered")
   @PreAuthorize("hasAuthority('REQUESTS_MANAGE')")
   @Audited(action = "DELIVERY_DELIVERED", entity = "DELIVERY_ORDER", id = "#id")
   @Operation(summary = "Доставлено", description = "Из COURIER_ORDERED или PICKED_UP — если покупатель не нажал "
         + "«Получил»")
   @ApiResponse(responseCode = "409", description = "BAD_STATUS")
   public AdminDeliveryOrderDto delivered(@PathVariable Long id) {
      AuditTrail.before(delivery.get(id));
      return delivery.delivered(id);
   }

   @PostMapping("/{id}/cancel")
   @PreAuthorize("hasAuthority('REQUESTS_MANAGE')")
   @Audited(action = "DELIVERY_CANCEL", entity = "DELIVERY_ORDER", id = "#id")
   @Operation(summary = "Отменить заказ")
   @ApiResponse(responseCode = "409", description = "BAD_STATUS")
   public AdminDeliveryOrderDto cancel(@PathVariable Long id) {
      AuditTrail.before(delivery.get(id));
      return delivery.cancel(id);
   }
}
