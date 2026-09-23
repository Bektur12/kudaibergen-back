package kg.kudaibergen.chat;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

   Page<Message> findByChatIdOrderByCreatedAtDesc(Long chatId, Pageable pageable);

   @Query("select count(m) from Message m where m.chatId = :chatId and m.senderId <> :userId and m.readAt is null")
   long countUnread(@Param("chatId") Long chatId, @Param("userId") Long userId);

   @Query("""
         select m.chatId from Message m
         where m.chatId in :chatIds and m.senderId <> :userId and m.readAt is null
         group by m.chatId
         """)
   List<Long> findChatIdsWithUnread(@Param("chatIds") List<Long> chatIds, @Param("userId") Long userId);

   @Modifying(clearAutomatically = true, flushAutomatically = true)
   @Query("""
         update Message m set m.readAt = :now
         where m.chatId = :chatId and m.senderId <> :userId and m.readAt is null
         """)
   int markRead(@Param("chatId") Long chatId, @Param("userId") Long userId, @Param("now") Instant now);
}
