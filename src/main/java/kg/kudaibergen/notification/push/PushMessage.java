package kg.kudaibergen.notification.push;

import java.util.Map;

/**
 * Пуш. category — набор кнопок в уведомлении (iOS category / Android click_action), например
 * NEW_REQUEST → «Есть» / «Нет»; null — без кнопок. sound = false — тихий (тихие часы, настройка звука).
 */
public record PushMessage(String title, String body, Map<String, String> data, String category, boolean sound) {

   public PushMessage(String title, String body, Map<String, String> data) {
      this(title, body, data, null, true);
   }
}
