package kg.kudaibergen.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Настройки уведомлений и темы (экраны 19 и 21). Строка создаётся вместе с пользователем. */
@Entity
@Table(name = "user_settings")
public class UserSettings {

   @Id
   @Column(name = "user_id")
   private Long userId;

   @Column(name = "notify_replies", nullable = false)
   private boolean notifyReplies = true;

   @Column(name = "notify_chat", nullable = false)
   private boolean notifyChat = true;

   @Column(name = "new_request_sound", nullable = false)
   private boolean newRequestSound = true;

   @Enumerated(EnumType.STRING)
   @Column(nullable = false, length = 6)
   private Theme theme = Theme.SYSTEM;

   protected UserSettings() {
   }

   public UserSettings(Long userId) {
      this.userId = userId;
   }

   public Long getUserId() {
      return userId;
   }

   public boolean isNotifyReplies() {
      return notifyReplies;
   }

   public void setNotifyReplies(boolean notifyReplies) {
      this.notifyReplies = notifyReplies;
   }

   public boolean isNotifyChat() {
      return notifyChat;
   }

   public void setNotifyChat(boolean notifyChat) {
      this.notifyChat = notifyChat;
   }

   public boolean isNewRequestSound() {
      return newRequestSound;
   }

   public void setNewRequestSound(boolean newRequestSound) {
      this.newRequestSound = newRequestSound;
   }

   public Theme getTheme() {
      return theme;
   }

   public void setTheme(Theme theme) {
      this.theme = theme;
   }
}
