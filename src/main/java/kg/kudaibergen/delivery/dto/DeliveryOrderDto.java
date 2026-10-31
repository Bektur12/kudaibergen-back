package kg.kudaibergen.delivery.dto;

import java.math.BigDecimal;
import java.time.Instant;

import kg.kudaibergen.delivery.entity.DeliveryDistrict;
import kg.kudaibergen.delivery.entity.DeliveryRegion;
import kg.kudaibergen.delivery.entity.DeliveryStatus;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.media.VideoDto;
import org.springframework.lang.Nullable;

/**
 * Заказ с доставкой: «Где посылка» покупателя и «Заказ с доставкой» продавца. buyer — только магазину
 * («покупатель Бакыт»), покупателю null. price — тариф до 5 кг (null — назовёт курьер). pickupToday — курьер
 * заберёт сегодня: упаковано (или, пока не упаковано, сейчас) до 17:30 по Бишкеку. courierPhone — «Курьер Ылдам».
 * courierWhatsappUrl — WhatsApp Ылдам с заполненным шаблоном: отправитель (продавец, адрес бокса или магазина, его
 * телефон), получатель, посылка — клиент договаривается с курьером сам.
 */
public record DeliveryOrderDto(Long id, String number, DeliveryStatus status, DeliveryPartDto part, DeliveryShopDto shop,
                               @Nullable DeliveryBuyerDto buyer, DeliveryRegion region, String regionName, String inName,
                               @Nullable DeliveryDistrict district, @Nullable String districtName, String address,
                               String recipientName, String recipientPhone, @Nullable Integer price, int days,
                               @Nullable BigDecimal weightKg, boolean fragile, @Nullable Long chatId,
                               boolean pickupToday, @Nullable String courierPhone, Instant createdAt,
                               @Nullable Instant packedAt, @Nullable Instant courierOrderedAt,
                               @Nullable Instant pickedUpAt, @Nullable Instant deliveredAt,
                               @Nullable Instant cancelledAt, String courierWhatsappUrl) {

   /** Товар снимком; id = null — товар удалён. */
   public record DeliveryPartDto(@Nullable Long id, String title, int price, @Nullable VideoDto video,
                              @Nullable PhotoDto mainPhoto) {
   }

   /** «Азамат · Ряд 14 · конт. 12»: sellerName — имя владельца бокса (или название), place — место. */
   public record DeliveryShopDto(Long id, String name, String sellerName, @Nullable String place,
                              @Nullable String phone) {
   }

   public record DeliveryBuyerDto(Long id, String name, @Nullable String phone) {
   }
}
