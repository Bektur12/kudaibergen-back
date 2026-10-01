package kg.kudaibergen.shop;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * verificationRequired — проверять ли, что продавец стоит в своём контейнере (QR, SMS арендатора, админ), до того
 * как магазин станет виден и начнёт получать запросы. Пока выключено: магазин действует сразу после регистрации,
 * переезд — сразу. Включить — SHOP_VERIFICATION_REQUIRED=true.
 * ignoreWorkingHours — только для разработки: считать боксы открытыми в любое время, чтобы запросы
 * доходили и ночью (SHOP_IGNORE_WORKING_HOURS=true). В проде — false.
 */
@ConfigurationProperties(prefix = "app.shops")
public record ShopProperties(boolean verificationRequired, boolean ignoreWorkingHours) {
}
