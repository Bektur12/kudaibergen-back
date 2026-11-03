package kg.kudaibergen.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Пуши на iPhone напрямую через Apple (APNs): приложение регистрирует сырой APNs-токен, не FCM.
 * key — содержимое .p8 целиком; production = false — песочница (сборки из Xcode).
 */
@ConfigurationProperties(prefix = "app.apns")
public record ApnsProperties(boolean enabled, String keyId, String teamId, String bundleId, String key,
                             boolean production) {
}
