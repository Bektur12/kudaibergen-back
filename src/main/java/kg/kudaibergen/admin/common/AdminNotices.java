package kg.kudaibergen.admin.common;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kg.kudaibergen.notification.push.PushMessage;
import kg.kudaibergen.notification.push.UserPushes;
import kg.kudaibergen.user.entity.Lang;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Пуши пользователям от администрации. Уходят после коммита: откатилось действие — пуша не будет.
 * <pre>
 * ADMIN_MESSAGE   {kind: MESSAGE | WARNING}                         — «Написать», предупреждение
 * ACCOUNT_STATUS  {target: SHOP | MASTER, id, event}                — подтверждён, отклонён, блокировка
 * DISPUTE_RESOLVED {disputeId, won: true | false}                   — спор за контейнер решён
 * </pre>
 */
@Component
public class AdminNotices {

   public enum Target { SHOP, MASTER }

   public enum StatusEvent { APPROVED, REJECTED, BLOCKED, UNBLOCKED }

   private final UserPushes pushes;

   public AdminNotices(UserPushes pushes) {
      this.pushes = pushes;
   }

   public void message(Collection<Long> userIds, String text) {
      afterCommit(userIds, lang -> new PushMessage(lang == Lang.KG ? "Базар администрациясы" : "Администрация рынка",
            text, Map.of("type", "ADMIN_MESSAGE", "kind", "MESSAGE")));
   }

   public void warning(Collection<Long> userIds, String reason) {
      afterCommit(userIds, lang -> new PushMessage(
            lang == Lang.KG ? "Администрациянын эскертүүсү" : "Предупреждение от администрации",
            reason, Map.of("type", "ADMIN_MESSAGE", "kind", "WARNING")));
   }

   public void status(Collection<Long> userIds, Target target, Long id, StatusEvent event, @Nullable String reason) {
      afterCommit(userIds, lang -> {
         Map<String, String> data = new HashMap<>(Map.of("type", "ACCOUNT_STATUS", "target", target.name(),
               "id", id.toString(), "event", event.name()));
         String title = statusTitle(target, event, lang);
         return new PushMessage(title, reason == null ? statusBody(event, lang) : reason, data);
      });
   }

   public void disputeResolved(Collection<Long> userIds, Long disputeId, boolean won, String container,
                               @Nullable String comment) {
      afterCommit(userIds, lang -> {
         String body = won
               ? (lang == Lang.KG ? container + " сизге бекитилди" : container + " закреплён за вами")
               : (lang == Lang.KG ? container + " башка ижарачыга берилди" : container + " передан другому арендатору");
         return new PushMessage(lang == Lang.KG ? "Контейнер боюнча талаш чечилди" : "Спор за контейнер решён",
               comment == null || comment.isBlank() ? body : body + ". " + comment,
               Map.of("type", "DISPUTE_RESOLVED", "disputeId", disputeId.toString(), "won", String.valueOf(won)));
      });
   }

   static String statusTitle(Target target, StatusEvent event, Lang lang) {
      boolean kg = lang == Lang.KG;
      return switch (event) {
         case APPROVED -> target == Target.SHOP
               ? (kg ? "Орун ырасталды" : "Место подтверждено") : (kg ? "Профиль ырасталды" : "Профиль подтверждён");
         case REJECTED -> target == Target.SHOP
               ? (kg ? "Орун ырасталган жок" : "Место не подтверждено") : (kg ? "Профиль ырасталган жок" : "Профиль не подтверждён");
         case BLOCKED -> target == Target.SHOP
               ? (kg ? "Дүкөн бөгөттөлдү" : "Магазин заблокирован") : (kg ? "Профиль бөгөттөлдү" : "Профиль заблокирован");
         case UNBLOCKED -> kg ? "Бөгөт алынды" : "Блокировка снята";
      };
   }

   private static String statusBody(StatusEvent event, Lang lang) {
      boolean kg = lang == Lang.KG;
      return switch (event) {
         case APPROVED, UNBLOCKED -> kg ? "Сурамдар кайра келет" : "Запросы снова будут приходить";
         case REJECTED, BLOCKED -> kg ? "Себебин тиркемеден караңыз" : "Причина — в приложении";
      };
   }

   private void afterCommit(Collection<Long> userIds, java.util.function.Function<Lang, PushMessage> message) {
      List<Long> recipients = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
      if (recipients.isEmpty()) {
         return;
      }
      Runnable send = () -> pushes.send(recipients, (user, settings) -> message.apply(user.getLang()));
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
         TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
               send.run();
            }
         });
      } else {
         send.run();
      }
   }
}
