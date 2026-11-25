package kg.kudaibergen.notification.push;

import java.time.Instant;
import java.util.Map;

/**
 * Пуш. category — набор кнопок в уведомлении (iOS category / Android click_action), например
 * NEW_REQUEST → «Есть» / «Нет»; null — без кнопок. sound = false — тихий (тихие часы, настройка звука).
 * liveActivity — iPhone с токеном Live Activity получает вместо пуша карточку на экране блокировки; null — обычный.
 */
public record PushMessage(String title, String body, Map<String, String> data, String category, boolean sound,
                          LiveActivity liveActivity) {

   public PushMessage(String title, String body, Map<String, String> data, String category, boolean sound) {
      this(title, body, data, category, sound, null);
   }

   public PushMessage(String title, String body, Map<String, String> data) {
      this(title, body, data, null, true, null);
   }

   /** Тот же пуш без кнопок — для устройств со старой версией приложения. */
   public PushMessage withoutActions() {
      return category == null ? this : new PushMessage(title, body, data, null, sound, liveActivity);
   }

   public PushMessage withLiveActivity(LiveActivity activity) {
      return new PushMessage(title, body, data, category, sound, activity);
   }

   /**
    * Карточка Live Activity: type — имя ActivityAttributes в приложении, attributes — неизменные поля карточки,
    * state — то, что может меняться (ContentState); staleAt — когда карточка устаревает (конец запроса).
    * Ключи — как поля в Swift; даты — числами секунд (JSONDecoder в ActivityKit не читает ISO).
    */
   public record LiveActivity(String type, Map<String, Object> attributes, Map<String, Object> state, Instant staleAt) {
   }
}
