package kg.kudaibergen.user.dto;

import kg.kudaibergen.user.entity.Theme;

/** Частичное обновление: null — настройка не меняется. */
public record UpdateSettingsRequest(Boolean notifyReplies, Boolean notifyChat, Boolean newRequestSound,
                                    Theme theme) {
}
