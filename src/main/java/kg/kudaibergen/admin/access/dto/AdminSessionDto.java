package kg.kudaibergen.admin.access.dto;

/**
 * Ответ входа и обновления сессии. accessToken — Bearer для /api/v1/admin/**, живёт expiresIn секунд.
 * Refresh-токен в теле не приходит: он в httpOnly-cookie admin_refresh (path /api/v1/admin/auth).
 */
public record AdminSessionDto(String accessToken, long expiresIn, AdminMeDto me) {
}
