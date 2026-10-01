package kg.kudaibergen.admin.access.dto;

import java.time.Instant;
import java.util.List;

import kg.kudaibergen.common.security.AdminPermission;
import kg.kudaibergen.common.security.AdminRole;
import org.springframework.lang.Nullable;

/**
 * Текущий сотрудник: шапка и сайдбар админки. permissions — что показывать в меню и на кнопках
 * (сервер всё равно проверяет каждое действие). badges — счётчики пунктов сайдбара.
 */
public record AdminMeDto(Long userId, String phone, String fullName, String title, AdminRole role,
                         List<AdminPermission> permissions, AdminBadgesDto badges, @Nullable Instant lastLoginAt) {

   /**
    * Бейджи сайдбара. null — у сотрудника нет права смотреть этот раздел.
    * shopsPending — продавцы на проверке (новые и переезды), mastersPending — мастера на проверке,
    * complaintsNew — жалобы без решения, disputesOpen — открытые споры за контейнер.
    */
   public record AdminBadgesDto(@Nullable Long shopsPending, @Nullable Long mastersPending,
                                @Nullable Long complaintsNew, @Nullable Long disputesOpen) {
   }
}
