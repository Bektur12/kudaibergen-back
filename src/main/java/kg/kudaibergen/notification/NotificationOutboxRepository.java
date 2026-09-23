package kg.kudaibergen.notification;

import java.util.List;

import kg.kudaibergen.notification.entity.NotificationOutbox;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

   @Query("""
         select n from NotificationOutbox n
         where n.sentAt is null and n.attempts < :maxAttempts
         order by n.createdAt
         """)
   List<NotificationOutbox> findUnsent(@Param("maxAttempts") short maxAttempts, Limit limit);
}
