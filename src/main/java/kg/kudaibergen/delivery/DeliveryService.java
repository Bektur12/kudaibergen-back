package kg.kudaibergen.delivery;

import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kg.kudaibergen.catalog.PartRepository;
import kg.kudaibergen.catalog.entity.Part;
import kg.kudaibergen.chat.ChatService;
import kg.kudaibergen.common.error.BadRequestException;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.delivery.dto.DeliveryInputs;
import kg.kudaibergen.delivery.dto.DeliveryOrderDto;
import kg.kudaibergen.delivery.dto.DeliveryTariffsDto;
import kg.kudaibergen.delivery.entity.DeliveryOrder;
import kg.kudaibergen.delivery.entity.DeliveryStatus;
import kg.kudaibergen.shop.ShopAccess;
import kg.kudaibergen.shop.ShopRepository;
import kg.kudaibergen.shop.entity.Shop;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Заказ с доставкой Ылдам Экспресс. Покупатель заказывает с видео товара («Доставка») — у продавца заказ и карточка
 * в чате; продавец упаковывает и отдаёт курьеру; курьера заказывает администрация (WhatsApp Ылдам); покупатель
 * нажимает «Получил». Оплату товара покупатель и продавец договариваются в чате.
 */
@Service
public class DeliveryService {

   /** Какие заказы показать в списке. */
   public enum DeliverySide { BUYER, SHOP }

   static final int LIST_LIMIT = 50;
   private static final Set<DeliveryStatus> ACTIVE = EnumSet.of(DeliveryStatus.NEW, DeliveryStatus.PACKED,
         DeliveryStatus.COURIER_ORDERED, DeliveryStatus.PICKED_UP);

   private final DeliveryOrderRepository orders;
   private final PartRepository parts;
   private final ShopRepository shops;
   private final ShopAccess shopAccess;
   private final ChatService chats;
   private final DeliveryTariffs tariffs;
   private final DeliveryView view;
   private final ApplicationEventPublisher events;

   public DeliveryService(DeliveryOrderRepository orders, PartRepository parts, ShopRepository shops,
                          ShopAccess shopAccess, ChatService chats, DeliveryTariffs tariffs, DeliveryView view,
                          ApplicationEventPublisher events) {
      this.orders = orders;
      this.parts = parts;
      this.shops = shops;
      this.shopAccess = shopAccess;
      this.chats = chats;
      this.tariffs = tariffs;
      this.view = view;
      this.events = events;
   }

   public DeliveryTariffsDto tariffs() {
      return tariffs.dto();
   }

   // ─────────────────────── покупатель ───────────────────────

   @Transactional
   public DeliveryOrderDto create(Long buyerId, DeliveryInputs.CreateOrder input) {
      Part part = parts.findById(input.partId())
            .filter(found -> found.isActive() && found.inStock() && found.isShipsToRegions())
            .orElseThrow(() -> new BadRequestException("DELIVERY_NOT_AVAILABLE",
                  "Этот товар продавец не отправляет — позвоните или напишите ему"));
      Shop shop = shops.findById(part.getShopId()).filter(Shop::isActive)
            .orElseThrow(() -> new BadRequestException("DELIVERY_NOT_AVAILABLE", "Магазин сейчас не принимает заказы"));
      Long ownShopId = shopAccess.membership(buyerId).map(membership -> membership.shop().getId()).orElse(null);
      if (shop.getId().equals(ownShopId)) {
         throw new BadRequestException("SELF_ORDER", "Нельзя заказать у своего контейнера");
      }
      DeliveryTariffs.Tariff tariff = tariffs.of(input.region(), input.district());
      DeliveryOrder order = new DeliveryOrder(part.getId(), shop.getId(), buyerId, part.getTitle(), part.getPrice(),
            input.region(), input.district(), input.address().strip(), input.recipientName().strip(),
            normalizePhone(input.recipientPhone()), tariff.price(), tariff.days(), tariffs.now());
      orders.saveAndFlush(order);
      order.assignNumber();
      Map<String, Object> card = new LinkedHashMap<>();
      card.put("deliveryOrderId", order.getId());
      card.put("number", order.getNumber());
      card.put("region", order.getRegion().name());
      card.put("price", order.getPrice());
      card.put("partId", part.getId());
      // подзаголовок карточки «Заказ с доставкой»: «KDB-000123 · в Ош» — цену доставки называет Ылдам
      String text = order.getNumber() + " · в " + order.getRegion().title();
      order.attachChat(chats.openForDelivery(buyerId, shop.getId(), text, card));
      events.publishEvent(new DeliveryEvents.DeliveryChanged(order.getId(), "CREATED"));
      return view.dto(order, false);
   }

   /** Покупатель видит свой заказ, люди бокса — заказы бокса. */
   @Transactional(readOnly = true)
   public DeliveryOrderDto get(Long userId, Long orderId) {
      DeliveryOrder order = orders.findById(orderId).orElseThrow(DeliveryService::notFound);
      return view.dto(order, side(userId, order) == DeliverySide.SHOP);
   }

   /** Последние заказы (до 50), новые сверху: на главной покупателя и сверху «Запросов» продавца. */
   @Transactional(readOnly = true)
   public List<DeliveryOrderDto> list(Long userId, DeliverySide side, boolean activeOnly) {
      Set<DeliveryStatus> statuses = activeOnly ? ACTIVE : EnumSet.allOf(DeliveryStatus.class);
      PageRequest page = PageRequest.of(0, LIST_LIMIT);
      List<DeliveryOrder> rows = side == DeliverySide.SHOP
            ? orders.findOfShop(shopAccess.requireMember(userId).shop().getId(), statuses, Long.MAX_VALUE, page)
            : orders.findOfBuyer(userId, statuses, Long.MAX_VALUE, page);
      return rows.stream().map(order -> view.dto(order, side == DeliverySide.SHOP)).toList();
   }

   /** «Получил» — покупатель, когда курьер привёз. */
   @Transactional
   public DeliveryOrderDto received(Long userId, Long orderId) {
      DeliveryOrder order = lock(orderId);
      if (!order.getBuyerId().equals(userId)) {
         throw notFound();
      }
      order.delivered(tariffs.now());
      events.publishEvent(new DeliveryEvents.DeliveryChanged(order.getId(), "DELIVERED"));
      return view.dto(order, false);
   }

   // ─────────────────────── продавец ───────────────────────

   /** «Упаковал — жду курьера»: вес и «Хрупкое» — в заявку Ылдам, админке — событие «Азамат упаковал». */
   @Transactional
   public DeliveryOrderDto packed(Long userId, Long orderId, DeliveryInputs.Packed input) {
      DeliveryOrder order = ownShopOrder(userId, orderId);
      order.packed(input.weightKg().setScale(1, RoundingMode.HALF_UP), Boolean.TRUE.equals(input.fragile()),
            tariffs.now());
      events.publishEvent(new DeliveryEvents.DeliveryChanged(order.getId(), "PACKED"));
      return view.dto(order, true);
   }

   /** «Курьер забрал» — покупателю пуш «едет в Ош». */
   @Transactional
   public DeliveryOrderDto pickedUp(Long userId, Long orderId) {
      DeliveryOrder order = ownShopOrder(userId, orderId);
      order.pickedUp(tariffs.now());
      events.publishEvent(new DeliveryEvents.DeliveryChanged(order.getId(), "PICKED_UP"));
      return view.dto(order, true);
   }

   // ─────────────────────── админка ───────────────────────

   /** «Отправить в WhatsApp Ылдам»: заявка ушла курьеру. */
   @Transactional
   public DeliveryOrder adminCourierOrdered(Long orderId) {
      DeliveryOrder order = lock(orderId);
      order.courierOrdered(tariffs.now());
      events.publishEvent(new DeliveryEvents.DeliveryChanged(order.getId(), "COURIER_ORDERED"));
      return order;
   }

   @Transactional
   public DeliveryOrder adminDelivered(Long orderId) {
      DeliveryOrder order = lock(orderId);
      order.delivered(tariffs.now());
      events.publishEvent(new DeliveryEvents.DeliveryChanged(order.getId(), "DELIVERED"));
      return order;
   }

   @Transactional
   public DeliveryOrder adminCancel(Long orderId) {
      DeliveryOrder order = lock(orderId);
      order.cancel(tariffs.now());
      events.publishEvent(new DeliveryEvents.DeliveryChanged(order.getId(), "CANCELLED"));
      return order;
   }

   // ─────────────────────── правила ───────────────────────

   private DeliveryOrder lock(Long orderId) {
      return orders.findForUpdate(orderId).orElseThrow(DeliveryService::notFound);
   }

   private DeliveryOrder ownShopOrder(Long userId, Long orderId) {
      Long shopId = shopAccess.requireMember(userId).shop().getId();
      DeliveryOrder order = lock(orderId);
      if (!order.getShopId().equals(shopId)) {
         throw notFound();
      }
      return order;
   }

   private DeliverySide side(Long userId, DeliveryOrder order) {
      if (order.getBuyerId().equals(userId)) {
         return DeliverySide.BUYER;
      }
      Long shopId = shopAccess.membership(userId).map(membership -> membership.shop().getId()).orElse(null);
      if (Objects.equals(shopId, order.getShopId())) {
         return DeliverySide.SHOP;
      }
      throw notFound();
   }

   /** «0555 123 456», «555123456», «+996 555 12 34 56» → +996555123456. */
   static String normalizePhone(String raw) {
      String digits = raw.replaceAll("\\D", "");
      if (digits.length() == 10 && digits.startsWith("0")) {
         digits = "996" + digits.substring(1);
      } else if (digits.length() == 9) {
         digits = "996" + digits;
      }
      if (digits.length() != 12 || !digits.startsWith("996")) {
         throw new BadRequestException("BAD_PHONE", "Телефон получателя: +996 и 9 цифр");
      }
      return "+" + digits;
   }

   static NotFoundException notFound() {
      return new NotFoundException("DELIVERY_ORDER_NOT_FOUND", "Заказ не найден");
   }
}
