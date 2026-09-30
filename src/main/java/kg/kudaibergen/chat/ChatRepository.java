package kg.kudaibergen.chat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import kg.kudaibergen.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRepository extends JpaRepository<Chat, Long> {

   /** Новое сообщение сдвигает last_message и отметки прочтения — по одному за раз. */
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("select c from Chat c where c.id = :id")
   Optional<Chat> findForUpdate(@Param("id") Long id);

   Optional<Chat> findByBuyerIdAndShopIdAndRequestId(Long buyerId, Long shopId, Long requestId);

   @Query("select c from Chat c where c.buyerId = :buyerId and c.shopId = :shopId and c.requestId is null")
   Optional<Chat> findDirect(@Param("buyerId") Long buyerId, @Param("shopId") Long shopId);

   List<Chat> findByRequestId(Long requestId);

   List<Chat> findByServiceRequestId(Long serviceRequestId);

   List<Chat> findByMasterIdAndServiceRequestIdIn(Long masterId, Collection<Long> serviceRequestIds);

   Optional<Chat> findByBuyerIdAndMasterIdAndServiceRequestId(Long buyerId, Long masterId, Long serviceRequestId);

   @Query("select c from Chat c where c.buyerId = :buyerId and c.masterId = :masterId and c.serviceRequestId is null")
   Optional<Chat> findDirectWithMaster(@Param("buyerId") Long buyerId, @Param("masterId") Long masterId);

   /** Чаты мастера (вкладка «Чаты» мастера). Пустые чаты мастер не видит. */
   @Query(nativeQuery = true, value = """
         select * from chats
         where master_id = :masterId and last_message_id is not null
           and (last_message_at, id) < (:at, :id)
         order by last_message_at desc, id desc
         limit :limit""")
   List<Chat> findMasterPage(@Param("masterId") Long masterId, @Param("at") Instant at, @Param("id") long id,
                             @Param("limit") int limit);

   @Query(nativeQuery = true, value = """
         select count(m.id) from chats c
         join messages m on m.chat_id = c.id and m.id > c.shop_read_message_id and m.side = 'BUYER'
         where c.master_id = :masterId and not c.blocked_by_shop""")
   long totalUnreadForMaster(@Param("masterId") Long masterId);

   List<Chat> findByShopIdAndRequestIdIn(Long shopId, Collection<Long> requestIds);

   /**
    * Чаты покупателя (16): свежие сверху. Ключ сортировки — время последнего сообщения, у пустого
    * чата — время создания. Курсор — (ключ, id) последней строки.
    */
   @Query(nativeQuery = true, value = """
         select * from chats
         where buyer_id = :buyerId
           and (coalesce(last_message_at, created_at), id) < (:at, :id)
         order by coalesce(last_message_at, created_at) desc, id desc
         limit :limit""")
   List<Chat> findBuyerPage(@Param("buyerId") Long buyerId, @Param("at") Instant at, @Param("id") long id,
                            @Param("limit") int limit);

   /** Чаты бокса (13, 16) — общий список владельца и сотрудников. Пустые чаты бокс не видит. */
   @Query(nativeQuery = true, value = """
         select * from chats
         where shop_id = :shopId and last_message_id is not null
           and (last_message_at, id) < (:at, :id)
         order by last_message_at desc, id desc
         limit :limit""")
   List<Chat> findShopPage(@Param("shopId") Long shopId, @Param("at") Instant at, @Param("id") long id,
                           @Param("limit") int limit);

   /** Непрочитанные покупателем: сообщения бокса после его отметки. [chatId, count] */
   @Query(nativeQuery = true, value = """
         select c.id, count(m.id) from chats c
         join messages m on m.chat_id = c.id and m.id > c.buyer_read_message_id and m.side = 'SHOP'
         where c.id in (:chatIds)
         group by c.id""")
   List<Object[]> countUnreadForBuyer(@Param("chatIds") Collection<Long> chatIds);

   /** Непрочитанные боксом: сообщения покупателя после отметки бокса. [chatId, count] */
   @Query(nativeQuery = true, value = """
         select c.id, count(m.id) from chats c
         join messages m on m.chat_id = c.id and m.id > c.shop_read_message_id and m.side = 'BUYER'
         where c.id in (:chatIds)
         group by c.id""")
   List<Object[]> countUnreadForShop(@Param("chatIds") Collection<Long> chatIds);

   /** Красный бейдж на вкладке «Чаты» у покупателя. */
   @Query(nativeQuery = true, value = """
         select count(m.id) from chats c
         join messages m on m.chat_id = c.id and m.id > c.buyer_read_message_id and m.side = 'SHOP'
         where c.buyer_id = :buyerId and not c.blocked_by_buyer""")
   long totalUnreadForBuyer(@Param("buyerId") Long buyerId);

   @Query(nativeQuery = true, value = """
         select count(m.id) from chats c
         join messages m on m.chat_id = c.id and m.id > c.shop_read_message_id and m.side = 'BUYER'
         where c.shop_id = :shopId and not c.blocked_by_shop""")
   long totalUnreadForShop(@Param("shopId") Long shopId);
}
