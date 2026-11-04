package kg.kudaibergen.shop;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * verificationRequired — новый магазин (на рынке и вне его) и мастер ждут подтверждения администрации, до того
 * как станут видны и начнут получать запросы. Выключено — действуют сразу после регистрации, переезд — сразу.
 * Включить — SHOP_VERIFICATION_REQUIRED=true.
 */
@ConfigurationProperties(prefix = "app.shops")
public record ShopProperties(boolean verificationRequired) {
}
