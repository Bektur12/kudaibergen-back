package kg.kudaibergen.catalog.comment;

import java.util.List;
import java.util.Map;

import kg.kudaibergen.catalog.PartRepository;
import kg.kudaibergen.notification.push.PushMessage;
import kg.kudaibergen.notification.push.QuietHours;
import kg.kudaibergen.notification.push.UserPushes;
import kg.kudaibergen.shop.ShopMemberRepository;
import kg.kudaibergen.shop.entity.ShopMember;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * После коммита: продавцу бокса — пуш «Новый комментарий» {type: PART_COMMENT, partId, commentId}. Свой
 * комментарий продавцу не шлём.
 */
@Component
public class PartCommentNotifier {

   private final PartRepository parts;
   private final ShopMemberRepository members;
   private final UserPushes pushes;
   private final QuietHours quietHours;

   public PartCommentNotifier(PartRepository parts, ShopMemberRepository members, UserPushes pushes,
                              QuietHours quietHours) {
      this.parts = parts;
      this.members = members;
      this.pushes = pushes;
      this.quietHours = quietHours;
   }

   @Async("appTaskExecutor")
   @TransactionalEventListener
   public void on(PartCommentService.CommentAdded event) {
      parts.findById(event.partId()).ifPresent(part -> {
         List<Long> staff = members.findByShopIdOrderByCreatedAtAsc(part.getShopId()).stream()
               .map(ShopMember::getUserId).filter(id -> !id.equals(event.authorId())).toList();
         if (staff.isEmpty()) {
            return;
         }
         Map<String, String> data = Map.of("type", "PART_COMMENT", "partId", part.getId().toString(),
               "commentId", event.commentId().toString());
         PushMessage message = new PushMessage("Новый комментарий", part.getTitle(), data, null, !quietHours.now());
         pushes.send(staff, (user, settings) -> message);
      });
   }
}
