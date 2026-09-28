package kg.kudaibergen.chat;

import java.util.List;
import java.util.Optional;

import kg.kudaibergen.chat.entity.ReplyTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplyTemplateRepository extends JpaRepository<ReplyTemplate, Long> {

   List<ReplyTemplate> findByShopIdOrderBySortOrderAscIdAsc(Long shopId);

   Optional<ReplyTemplate> findByIdAndShopId(Long id, Long shopId);

   long countByShopId(Long shopId);
}
