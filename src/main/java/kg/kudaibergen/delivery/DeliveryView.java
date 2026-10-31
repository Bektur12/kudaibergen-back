package kg.kudaibergen.delivery;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import kg.kudaibergen.catalog.PartRepository;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.delivery.dto.DeliveryOrderDto;
import kg.kudaibergen.delivery.entity.DeliveryOrder;
import kg.kudaibergen.delivery.entity.DeliveryStatus;
import kg.kudaibergen.media.MediaService;
import kg.kudaibergen.media.PhotoDto;
import kg.kudaibergen.media.VideoDto;
import kg.kudaibergen.shop.ShopMapper;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.User;
import org.springframework.stereotype.Component;

/** Сборка заказа с доставкой для покупателя, продавца и админки, заявка Ылдам и ссылка WhatsApp. */
@Component
public class DeliveryView {

   private final PartRepository parts;
   private final ShopRepository shops;
   private final ShopMapper shopMapper;
   private final UserRepository users;
   private final MediaService media;
   private final DeliveryTariffs tariffs;

   public DeliveryView(PartRepository parts, ShopRepository shops, ShopMapper shopMapper, UserRepository users,
                       MediaService media, DeliveryTariffs tariffs) {
      this.parts = parts;
      this.shops = shops;
      this.shopMapper = shopMapper;
      this.users = users;
      this.media = media;
      this.tariffs = tariffs;
   }

   /** Продавец для заявки и шапки: имя владельца бокса (или название бокса), адрес на рынке, телефон. */
   public record Seller(Long shopId, String shopName, String name, String place, String address, String phone) {
   }

   public DeliveryOrderDto dto(DeliveryOrder order, boolean forShop) {
      Seller seller = seller(order.getShopId());
      User buyer = forShop ? users.findById(order.getBuyerId()).orElse(null) : null;
      return new DeliveryOrderDto(order.getId(), order.getNumber(), order.getStatus(), part(order),
            new DeliveryOrderDto.DeliveryShopDto(seller.shopId(), seller.shopName(), seller.name(), seller.place(),
                  seller.phone()),
            buyer == null ? null : new DeliveryOrderDto.DeliveryBuyerDto(buyer.getId(), buyerName(order, buyer),
                  buyer.getPhone()),
            order.getRegion(), order.getRegion().title(), order.getRegion().inName(), order.getDistrict(),
            order.getDistrict() == null ? null : order.getDistrict().title(), order.getAddress(),
            order.getRecipientName(), order.getRecipientPhone(), order.getPrice(), order.getDays(),
            order.getWeightKg(), order.isFragile(), order.getChatId(), pickupToday(order),
            tariffs.properties().courierPhone(), order.getCreatedAt(), order.getPackedAt(),
            order.getCourierOrderedAt(), order.getPickedUpAt(), order.getDeliveredAt(), order.getCancelledAt(),
            whatsappUrl(template(order, seller)));
   }

   public DeliveryOrderDto.DeliveryPartDto part(DeliveryOrder order) {
      Part part = order.getPartId() == null ? null : parts.findById(order.getPartId()).orElse(null);
      VideoDto video = part == null || !part.hasVideo() ? null
            : media.videos(List.of(part.getVideoMediaId())).get(part.getVideoMediaId());
      PhotoDto photo = part == null || part.getPhotoIds().isEmpty() ? null
            : media.photos(List.of(part.getPhotoIds().get(0))).get(part.getPhotoIds().get(0));
      return new DeliveryOrderDto.DeliveryPartDto(part == null ? null : part.getId(), order.getPartTitle(),
            order.getPartPrice(), video, photo);
   }

   public Seller seller(Long shopId) {
      Shop shop = shops.findById(shopId).orElseThrow();
      User owner = users.findById(shop.getOwnerId()).orElse(null);
      String name = owner != null && owner.getName() != null && !owner.getName().isBlank()
            ? owner.getName() : shop.getName();
      String place = shopMapper.place(shop);
      // вне рынка — адрес самого магазина; на рынке — «рынок Кудайберген, ряд 14, конт. 12»
      String address = !shop.isOnMarket() ? shop.getAddress() : place == null ? "рынок Кудайберген"
            : "рынок Кудайберген, " + place.toLowerCase(Locale.ROOT).replace(" · ", ", ");
      String phone = shop.getPhone() != null ? shop.getPhone() : owner == null ? null : owner.getPhone();
      return new Seller(shop.getId(), shop.getName(), name, place, address, phone);
   }

   /** Курьер заберёт сегодня: упаковано до отсечки; не упаковано — если упакуют сейчас. */
   public boolean pickupToday(DeliveryOrder order) {
      if (order.getStatus() == DeliveryStatus.NEW) {
         return tariffs.pickupToday(tariffs.now());
      }
      return order.getPackedAt() != null && tariffs.pickupToday(order.getPackedAt());
   }

   /** Шаблон «Данные для оформления заказа» Ылдам — строки как в их форме. */
   public String template(DeliveryOrder order, Seller seller) {
      String city = order.getDistrict() != null ? order.getDistrict().city() : order.getRegion().city();
      return String.join("\n",
            "📋Данные для оформления заказа",
            "Отправитель:",
            "Имя: " + seller.name(),
            "Адрес: " + seller.address(),
            "Телефон: " + phone(seller.phone()),
            "Получатель:",
            "Имя: " + order.getRecipientName(),
            "Адрес: " + order.getAddress(),
            "Телефон: " + phone(order.getRecipientPhone()),
            "Район / населённый пункт: " + city,
            "Посылка:",
            "Размер / вес: " + (order.getWeightKg() == null ? "—" : kg(order.getWeightKg()) + " кг"),
            "Хрупкость: " + (order.isFragile() ? "Да" : "Нет"),
            "Примечание: автозапчасть, заказ " + order.getNumber());
   }

   /** wa.me с готовым текстом; номер Ылдам не задан — WhatsApp спросит, кому отправить. */
   public String whatsappUrl(String text) {
      String number = tariffs.properties().courierWhatsapp();
      String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
      return "https://wa.me/" + (number == null ? "" : number.replaceAll("\\D", "")) + "?text=" + encoded;
   }

   /** «+996 555 123 456». */
   static String phone(String phone) {
      if (phone == null) {
         return "—";
      }
      String digits = phone.replaceAll("\\D", "");
      if (digits.length() == 12 && digits.startsWith("996")) {
         return "+996 " + digits.substring(3, 6) + " " + digits.substring(6, 9) + " " + digits.substring(9);
      }
      return phone;
   }

   /** 3 → «3», 2.5 → «2,5». */
   static String kg(BigDecimal weight) {
      return weight.stripTrailingZeros().toPlainString().replace('.', ',');
   }

   private static String buyerName(DeliveryOrder order, User buyer) {
      return buyer.getName() != null && !buyer.getName().isBlank() ? buyer.getName() : order.getRecipientName();
   }
}
