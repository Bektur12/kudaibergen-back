package kg.kudaibergen.notification.push;

import java.util.List;

/** Отправка пуша на устройства. Реализация — FCM или лог в dev. */
public interface PushSender {

   void send(List<String> deviceTokens, PushMessage message);
}
