package kg.kudaibergen.admin.delivery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.admin.common.AdminPage;
import kg.kudaibergen.common.web.CursorPage;
import kg.kudaibergen.delivery.DeliveryOrderRepository;
import kg.kudaibergen.delivery.DeliveryService;
import kg.kudaibergen.delivery.DeliveryTariffs;
import kg.kudaibergen.delivery.DeliveryView;
import kg.kudaibergen.delivery.entity.DeliveryOrder;
import kg.kudaibergen.delivery.entity.DeliveryStatus;
import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.entity.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Доставки в админке: список с поиском и счётчиками табов, заявка Ылдам, «заказали курьера», «доставлено». */
@Service
public class AdminDeliveryService {

   private final DeliveryOrderRepository orders;
   private final DeliveryService delivery;
   private final DeliveryView view;
   private final DeliveryTariffs tariffs;
   private final UserRepository users;
   private final NamedParameterJdbcTemplate jdbc;

   public AdminDeliveryService(DeliveryOrderRepository orders, DeliveryService delivery, DeliveryView view,
                               DeliveryTariffs tariffs, UserRepository users, NamedParameterJdbcTemplate jdbc) {
      this.orders = orders;
      this.delivery = delivery;
      this.view = view;
      this.tariffs = tariffs;
      this.users = users;
      this.jdbc = jdbc;
   }

   /** q — номер («KDB-12», «12»), название товара или цифры телефона получателя или покупателя. */
   @Transactional(readOnly = true)
   public AdminPage<AdminDeliveryOrderDto, Map<String, Long>> list(DeliveryStatus status, String q, String cursor,
                                                                   Integer limit) {
      int size = CursorPage.limit(limit);
      long beforeId = cursor == null || cursor.isBlank() ? Long.MAX_VALUE : CursorPage.afterId(cursor);
      MapSqlParameterSource params = new MapSqlParameterSource();
      String search = searchCondition(q, params);
      String where = "where o.id < :beforeId" + (status == null ? "" : " and o.status = :status") + search;
      params.addValue("beforeId", beforeId).addValue("status", status == null ? null : status.name())
            .addValue("limit", size + 1);
      List<Long> ids = jdbc.queryForList("select o.id from delivery_orders o join users u on u.id = o.buyer_id "
            + where + " order by o.id desc limit :limit", params, Long.class);
      boolean more = ids.size() > size;
      List<Long> page = more ? ids.subList(0, size) : ids;
      Map<Long, DeliveryOrder> byId = orders.findAllById(page).stream()
            .collect(Collectors.toMap(DeliveryOrder::getId, Function.identity()));
      List<AdminDeliveryOrderDto> items = new ArrayList<>();
      page.forEach(id -> {
         DeliveryOrder order = byId.get(id);
         if (order != null) {
            items.add(dto(order));
         }
      });
      return new AdminPage<>(items, more ? CursorPage.encode(String.valueOf(page.get(page.size() - 1))) : null,
            counts(q));
   }

   @Transactional(readOnly = true)
   public AdminDeliveryOrderDto get(Long id) {
      return dto(orders.findById(id).orElseThrow(AdminDeliveryService::notFound));
   }

   @Transactional
   public AdminDeliveryOrderDto courierOrdered(Long id) {
      return dto(delivery.adminCourierOrdered(id));
   }

   @Transactional
   public AdminDeliveryOrderDto delivered(Long id) {
      return dto(delivery.adminDelivered(id));
   }

   @Transactional
   public AdminDeliveryOrderDto cancel(Long id) {
      return dto(delivery.adminCancel(id));
   }

   AdminDeliveryOrderDto dto(DeliveryOrder order) {
      DeliveryView.Seller seller = view.seller(order.getShopId());
      User buyer = users.findById(order.getBuyerId()).orElse(null);
      String template = view.template(order, seller);
      return new AdminDeliveryOrderDto(order.getId(), order.getNumber(), order.getStatus(), view.part(order),
            new AdminDeliveryOrderDto.AdminDeliverySellerDto(seller.shopId(), seller.name(), seller.shopName(), seller.address(),
                  seller.place(), seller.phone()),
            new AdminDeliveryOrderDto.AdminDeliveryBuyerDto(order.getBuyerId(), buyer == null ? null : buyer.getName(),
                  buyer == null ? null : buyer.getPhone()),
            order.getRegion(), order.getRegion().title(), order.getRegion().inName(), order.getDistrict(),
            order.getDistrict() == null ? null : order.getDistrict().title(), order.getAddress(),
            order.getRecipientName(), order.getRecipientPhone(), order.getPrice(), order.getDays(),
            order.getWeightKg(), order.isFragile(), order.getChatId(), order.getCreatedAt(), order.getPackedAt(),
            order.getCourierOrderedAt(), order.getPickedUpAt(), order.getDeliveredAt(), order.getCancelledAt(),
            tariffs.cutoffAt(), template, view.whatsappUrl(template), view.pickupToday(order));
   }

   /** Табы: all и по статусам — с учётом поиска. */
   private Map<String, Long> counts(String q) {
      MapSqlParameterSource params = new MapSqlParameterSource();
      String search = searchCondition(q, params);
      Map<String, Long> counts = new LinkedHashMap<>();
      counts.put("all", 0L);
      for (DeliveryStatus status : DeliveryStatus.values()) {
         counts.put(status.name(), 0L);
      }
      jdbc.query("select o.status, count(*) as n from delivery_orders o join users u on u.id = o.buyer_id where true"
            + search + " group by o.status", params, rs -> {
               counts.put(rs.getString("status"), rs.getLong("n"));
               counts.merge("all", rs.getLong("n"), Long::sum);
            });
      return counts;
   }

   private static String searchCondition(String q, MapSqlParameterSource params) {
      if (q == null || q.isBlank()) {
         return "";
      }
      String text = q.strip().toLowerCase();
      params.addValue("qLike", "%" + text.replace("%", "").replace("_", "") + "%");
      String digits = text.replaceAll("\\D", "");
      List<String> any = new ArrayList<>();
      any.add("lower(o.number) like :qLike");
      any.add("lower(o.part_title) like :qLike");
      if (!digits.isEmpty()) {
         params.addValue("qDigits", "%" + digits + "%");
         any.add("o.number like :qDigits");
         if (digits.length() >= 3) {
            any.add("regexp_replace(o.recipient_phone, '\\D', '', 'g') like :qDigits");
            any.add("regexp_replace(u.phone, '\\D', '', 'g') like :qDigits");
         }
      }
      return " and (" + String.join(" or ", any) + ")";
   }

   static NotFoundException notFound() {
      return new NotFoundException("DELIVERY_ORDER_NOT_FOUND", "Заказ не найден");
   }
}
