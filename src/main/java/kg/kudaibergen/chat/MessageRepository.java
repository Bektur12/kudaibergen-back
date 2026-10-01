package kg.kudaibergen.chat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import kg.kudaibergen.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

   /** История чата: новые первыми, beforeId — id самого старого уже загруженного сообщения. */
   @Query("select m from Message m where m.chatId = :chatId and m.id < :beforeId order by m.id desc")
   List<Message> findPage(@Param("chatId") Long chatId, @Param("beforeId") long beforeId, Pageable page);

   Optional<Message> findByChatIdAndClientId(Long chatId, String clientId);

   List<Message> findByIdIn(Collection<Long> ids);
}
