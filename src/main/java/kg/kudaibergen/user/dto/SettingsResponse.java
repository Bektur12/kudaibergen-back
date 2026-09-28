package kg.kudaibergen.user.dto;

import kg.kudaibergen.user.entity.Theme;
import kg.kudaibergen.user.entity.UserSettings;

public record SettingsResponse(boolean notifyReplies, boolean notifyChat, boolean newRequestSound, Theme theme) {

   public static SettingsResponse of(UserSettings settings) {
      return new SettingsResponse(settings.isNotifyReplies(), settings.isNotifyChat(),
            settings.isNewRequestSound(), settings.getTheme());
   }
}
