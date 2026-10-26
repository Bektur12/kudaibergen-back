package kg.kudaibergen.admin.delivery;

import java.math.BigDecimal;
import java.time.Instant;

import kg.kudaibergen.delivery.dto.DeliveryOrderDto;
import kg.kudaibergen.delivery.entity.DeliveryDistrict;
import kg.kudaibergen.delivery.entity.DeliveryRegion;
import kg.kudaibergen.delivery.entity.DeliveryStatus;
import org.springframework.lang.Nullable;

/**
 * Заказ с доставкой в админке: «Заказать курьера». template — «Данные для оформления заказа» Ылдам из заказа,
 * whatsappUrl — wa.me с этим текстом, cutoffAt — сегодня 17:30 по Бишкеку («ещё 6 ч 25 мин»), pickupToday —
 * курьер заберёт сегодня. Телефоны — полные (они уходят курьеру).
 */
public record AdminDeliveryOrderDto(Long id, String number, DeliveryStatus status, DeliveryOrderDto.DeliveryPartDto part,
                                    AdminDeliverySellerDto seller, AdminDeliveryBuyerDto buyer, DeliveryRegion region, String regionName,
                                    String inName, @Nullable DeliveryDistrict district, @Nullable String districtName,
                                    String address, String recipientName, String recipientPhone,
                                    @Nullable Integer price, int days, @Nullable BigDecimal weightKg, boolean fragile,
                                    @Nullable Long chatId, Instant createdAt, @Nullable Instant packedAt,
                                    @Nullable Instant courierOrderedAt, @Nullable Instant pickedUpAt,
                                    @Nullable Instant deliveredAt, @Nullable Instant cancelledAt, Instant cutoffAt,
                                    String template, String whatsappUrl, boolean pickupToday) {

   /** name — «Азамат» (владелец бокса, иначе название), address — «рынок Кудайберген, ряд 14, конт. 12». */
   public record AdminDeliverySellerDto(Long shopId, String name, String shopName, String address, @Nullable String place,
                           @Nullable String phone) {
   }

   public record AdminDeliveryBuyerDto(Long userId, @Nullable String name, @Nullable String phone) {
   }
}
