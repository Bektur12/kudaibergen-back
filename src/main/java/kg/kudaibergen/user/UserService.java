package kg.kudaibergen.user;

import java.time.Duration;
import java.time.Instant;

import kg.kudaibergen.common.error.NotFoundException;
import kg.kudaibergen.user.dto.UpdateMeRequest;
import kg.kudaibergen.user.dto.UpdateSettingsRequest;
import kg.kudaibergen.user.entity.Lang;
import kg.kudaibergen.user.entity.User;
import kg.kudaibergen.user.entity.UserRole;
import kg.kudaibergen.user.entity.UserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

   /** Через сколько после подтверждения удаления аккаунта стираются данные (ТЗ, раздел 3). */
   public static final Duration DELETION_GRACE = Duration.ofDays(30);

   private static final Logger log = LoggerFactory.getLogger(UserService.class);

   private final UserRepository users;
   private final UserSettingsRepository settings;

   public UserService(UserRepository users, UserSettingsRepository settings) {
      this.users = users;
      this.settings = settings;
   }

   @Transactional(readOnly = true)
   public User getRequired(Long id) {
      return users.findById(id)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Пользователь не найден"));
   }

   /** Пользователь по номеру; новый номер регистрируется с языком, выбранным на экране 01. */
   @Transactional
   public User findOrCreate(String phone, Lang lang) {
      if (users.insertIfAbsent(phone, lang.name()) == 1) {
         log.info("Регистрация нового пользователя {}", phone);
      }
      User user = users.findByPhone(phone).orElseThrow();
      settings.insertDefaults(user.getId());
      return user;
   }

   @Transactional
   public User updateProfile(Long userId, UpdateMeRequest request) {
      User user = getRequired(userId);
      if (request.name() != null) {
         user.setName(request.name().trim());
      }
      if (request.lang() != null) {
         user.setLang(request.lang());
      }
      return user;
   }

   @Transactional
   public User changeRole(Long userId, UserRole role) {
      User user = getRequired(userId);
      user.switchRole(role);
      return user;
   }

   @Transactional(readOnly = true)
   public UserSettings settings(Long userId) {
      return settings.findById(userId).orElseGet(() -> new UserSettings(userId));
   }

   @Transactional
   public UserSettings updateSettings(Long userId, UpdateSettingsRequest request) {
      UserSettings current = settings.findById(userId).orElseGet(() -> settings.save(new UserSettings(userId)));
      if (request.notifyReplies() != null) {
         current.setNotifyReplies(request.notifyReplies());
      }
      if (request.notifyChat() != null) {
         current.setNotifyChat(request.notifyChat());
      }
      if (request.newRequestSound() != null) {
         current.setNewRequestSound(request.newRequestSound());
      }
      if (request.theme() != null) {
         current.setTheme(request.theme());
      }
      return current;
   }

   /**
    * Стирает аккаунты, удаление которых подтверждено больше 30 дней назад. Связанные данные
    * уходят каскадом; отзывы (модуль requests) остаются без автора — FK ON DELETE SET NULL.
    */
   @Scheduled(cron = "0 0 4 * * *")
   @Transactional
   public void purgeDeletedAccounts() {
      int purged = users.deleteRequestedBefore(Instant.now().minus(DELETION_GRACE));
      if (purged > 0) {
         log.info("Стёрто аккаунтов по истечении 30 дней: {}", purged);
      }
   }

   @Transactional
   public void touchLastSeen(Long userId, Instant at) {
      users.touchLastSeen(userId, at);
   }
}
