package kg.kudaibergen.user;

import java.util.List;

import kg.kudaibergen.user.entity.DeviceToken;
import kg.kudaibergen.user.entity.Platform;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

   private final DeviceTokenRepository devices;

   public DeviceService(DeviceTokenRepository devices) {
      this.devices = devices;
   }

   /**
    * Идемпотентно: повторная регистрация того же токена лишь обновляет владельца, время и умение кнопок.
    * actions — приложение само рисует пуши с кнопками (новые версии); иначе на Android шлём обычный пуш.
    */
   @Transactional
   public void register(Long userId, String token, Platform platform, boolean actions) {
      register(userId, token, platform, actions, null);
   }

   /** liveActivityToken — push-to-start токен Live Activity (iPhone); null — прежний остаётся. */
   @Transactional
   public void register(Long userId, String token, Platform platform, boolean actions, String liveActivityToken) {
      DeviceToken device = devices.findByToken(token).map(existing -> {
         existing.reassign(userId, platform, actions);
         return existing;
      }).orElseGet(() -> devices.save(new DeviceToken(userId, token, platform, actions)));
      device.liveActivityToken(liveActivityToken);
   }

   @Transactional
   public void removeAllOf(Long userId) {
      devices.deleteAllOfUser(userId);
   }

   @Transactional
   public void unregister(Long userId, String token) {
      devices.deleteOwned(token, userId);
   }

   @Transactional(readOnly = true)
   public List<String> tokensOf(Long userId) {
      return devices.findByUserId(userId).stream().map(DeviceToken::getToken).toList();
   }

   @Transactional
   public void removeInvalid(List<String> tokens) {
      if (!tokens.isEmpty()) {
         devices.deleteByTokens(tokens);
      }
   }
}
