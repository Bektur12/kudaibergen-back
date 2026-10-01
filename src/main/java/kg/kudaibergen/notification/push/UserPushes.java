package kg.kudaibergen.notification.push;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import kg.kudaibergen.user.DeviceTokenRepository;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.UserSettingsRepository;
import kg.kudaibergen.user.entity.DeviceToken;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Пуш пользователям на все их устройства. Сообщение собирается под каждого получателя: его язык
 * и настройки уведомлений; factory вернул null — этому пользователю не шлём. Ошибка одного
 * получателя не мешает остальным.
 */
@Component
public class UserPushes {

   private static final Logger log = LoggerFactory.getLogger(UserPushes.class);

   private final DeviceTokenRepository devices;
   private final UserRepository users;
   private final UserSettingsRepository settings;
   private final PushSender push;

   public UserPushes(DeviceTokenRepository devices, UserRepository users, UserSettingsRepository settings,
                     PushSender push) {
      this.devices = devices;
      this.users = users;
      this.settings = settings;
      this.push = push;
   }

   public void send(Collection<Long> userIds, MessageFactory factory) {
      if (userIds.isEmpty()) {
         return;
      }
      Map<Long, List<String>> tokens = devices.findByUserIdIn(userIds).stream()
            .collect(Collectors.groupingBy(DeviceToken::getUserId,
                  Collectors.mapping(DeviceToken::getToken, Collectors.toList())));
      if (tokens.isEmpty()) {
         return;
      }
      Map<Long, UserSettings> settingsById = settings.findAllById(tokens.keySet()).stream()
            .collect(Collectors.toMap(UserSettings::getUserId, Function.identity()));
      for (User user : users.findAllById(tokens.keySet())) {
         PushMessage message = factory.create(user,
               settingsById.getOrDefault(user.getId(), new UserSettings(user.getId())));
         if (message == null) {
            continue;
         }
         try {
            push.send(tokens.get(user.getId()), message);
         } catch (RuntimeException ex) {
            log.warn("Пуш пользователю {} не отправлен: {}", user.getId(), ex.getMessage());
         }
      }
   }

   /** Сообщение под получателя; null — не отправлять. */
   @FunctionalInterface
   public interface MessageFactory {
      PushMessage create(User user, UserSettings settings);
   }
}
