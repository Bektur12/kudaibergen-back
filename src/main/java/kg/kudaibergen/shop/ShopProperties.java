package kg.kudaibergen.shop;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * verificationRequired — проверять ли, что продавец стоит в своём контейнере (QR, SMS арендатора, админ), до того
 * как магазин станет виден и начнёт получать запросы. Пока выключено: магазин действует сразу после регистрации,
 * переезд — сразу. Включить — SHOP_VERIFICATION_REQUIRED=true.
 */
@ConfigurationProperties(prefix = "app.shops")
public record ShopProperties(boolean verificationRequired) {
}
