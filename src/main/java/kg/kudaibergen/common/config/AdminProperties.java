package kg.kudaibergen.common.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Веб-админка (app.admin.*).
 * origins — домены админки для CORS (ADMIN_ORIGINS через запятую; шаблоны вида http://localhost:*).
 * accessTtl / refreshTtl — жизнь токена и сессии; refresh лежит в httpOnly-cookie {@code admin_refresh}.
 * cookieSecure / cookieSameSite — атрибуты этой cookie (в проде Secure и Strict, если админка на том же сайте).
 * bootstrapPhone / bootstrapName — первый SUPER_ADMIN создаётся при старте, если активного суперадмина нет.
 * loginLimit / loginWindow — попыток входа на номер за окно (на IP — втрое больше).
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(List<String> origins, Duration accessTtl, Duration refreshTtl, boolean cookieSecure,
                              String cookieSameSite, String bootstrapPhone, String bootstrapName, int loginLimit,
                              Duration loginWindow) {
}
