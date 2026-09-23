package kg.kudaibergen.chat;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.chat.entity.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRepository extends JpaRepository<Chat, Long> {

   /** Один чат на пару покупатель-магазин, независимо от того, какой запрос/оффер его
    * запустил — второй заход (другой запрос, или «написать» с карточки магазина) должен
    * продолжить ту же переписку, а не открыть параллельный тред. */
   Optional<Chat> findByBuyerIdAndStoreId(Long buyerId, Long storeId);

   @Query("""
         select c from Chat c
         where c.buyerId = :userId or c.storeId = :storeId
         order by coalesce(c.lastMessageAt, c.createdAt) desc
         """)
   List<Chat> findForParticipant(@Param("userId") Long userId, @Param("storeId") Long storeId);

   boolean existsByBuyerIdAndStoreId(Long buyerId, Long storeId);
}
