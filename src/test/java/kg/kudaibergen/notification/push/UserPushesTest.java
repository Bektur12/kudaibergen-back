package kg.kudaibergen.notification.push;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import kg.kudaibergen.user.DeviceTokenRepository;
import kg.kudaibergen.user.UserRepository;
import kg.kudaibergen.user.UserSettingsRepository;
import kg.kudaibergen.user.entity.DeviceToken;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.Platform;
import kg.kudaibergen.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Пуш с кнопками — только новым версиям приложения на Android; старым тот же пуш без кнопок. */
class UserPushesTest {

   @Test
   void кнопкиТолькоНовымВерсиямНаAndroid() {
      DeviceTokenRepository devices = mock(DeviceTokenRepository.class);
      UserRepository users = mock(UserRepository.class);
      UserSettingsRepository settings = mock(UserSettingsRepository.class);
      PushSender push = mock(PushSender.class);
      User user = new User("+996700000001", Lang.RU);
      ReflectionTestUtils.setField(user, "id", 1L);
      when(devices.findByUserIdIn(any())).thenReturn(List.of(
            new DeviceToken(1L, "android-new", Platform.ANDROID, true),
            new DeviceToken(1L, "android-old", Platform.ANDROID, false),
            new DeviceToken(1L, "iphone", Platform.IOS, false)));
      when(users.findAllById(anyCollection())).thenReturn(List.of(user));
      when(settings.findAllById(anyCollection())).thenReturn(List.of());

      PushMessage withButtons = new PushMessage("Toyota Camry", "Фара", Map.of("type", "NEW_REQUEST"), "NEW_REQUEST", true);
      new UserPushes(devices, users, settings, push, Optional.empty()).send(Set.of(1L), (u, s) -> withButtons);

      // новое приложение на Android и iPhone (кнопки — категорией iOS) — с кнопками; старое на Android — обычный пуш
      verify(push).send(List.of("android-new", "iphone"), withButtons);
      verify(push).send(List.of("android-old"), withButtons.withoutActions());
   }

   @Test
   void iPhoneСLiveActivityПолучаетКарточкуОстальныеОбычныйПуш() {
      DeviceTokenRepository devices = mock(DeviceTokenRepository.class);
      UserRepository users = mock(UserRepository.class);
      UserSettingsRepository settings = mock(UserSettingsRepository.class);
      PushSender push = mock(PushSender.class);
      ApnsPushSender apns = mock(ApnsPushSender.class);
      User user = new User("+996700000001", Lang.RU);
      ReflectionTestUtils.setField(user, "id", 1L);
      DeviceToken live = new DeviceToken(1L, "iphone-new", Platform.IOS, true);
      live.liveActivityToken("start-token");
      when(devices.findByUserIdIn(any())).thenReturn(List.of(
            live,
            new DeviceToken(1L, "iphone-old", Platform.IOS, true),
            new DeviceToken(1L, "android", Platform.ANDROID, true)));
      when(users.findAllById(anyCollection())).thenReturn(List.of(user));
      when(settings.findAllById(anyCollection())).thenReturn(List.of());

      PushMessage message = new PushMessage("Новый запрос", "Фара", Map.of("type", "NEW_REQUEST"), "NEW_REQUEST", true)
            .withLiveActivity(new PushMessage.LiveActivity("RequestActivityAttributes", Map.of("requestId", 7L),
                  Map.of("status", "WAITING"), null));
      new UserPushes(devices, users, settings, push, Optional.of(apns)).send(Set.of(1L), (u, s) -> message);

      // карточка — только iPhone с токеном Live Activity; второму iPhone — обычный пуш через APNs, Android — через FCM
      verify(apns).startLiveActivity(List.of("start-token"), message);
      verify(apns).send(List.of("iphone-old"), message);
      verify(push).send(List.of("android"), message);
   }
}
