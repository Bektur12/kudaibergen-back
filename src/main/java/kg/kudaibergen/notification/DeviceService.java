package kg.kudaibergen.notification;

import kg.kudaibergen.notification.dto.RegisterDeviceRequest;
import kg.kudaibergen.notification.entity.DeviceToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

   private final DeviceTokenRepository deviceTokens;

   public DeviceService(DeviceTokenRepository deviceTokens) {
      this.deviceTokens = deviceTokens;
   }

   /** Токен уникален глобально: если устройство перелогинилось, оно переезжает к новому пользователю. */
   @Transactional
   public void register(Long userId, RegisterDeviceRequest request) {
      deviceTokens.findByToken(request.token()).ifPresentOrElse(existing -> {
         existing.setUserId(userId);
         existing.setPlatform(request.platform());
      }, () -> deviceTokens.save(new DeviceToken(userId, request.token(), request.platform())));
   }

   @Transactional
   public void unregister(Long userId, String token) {
      deviceTokens.deleteByTokenAndUserId(token, userId);
   }
}
