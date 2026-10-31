package kg.kudaibergen.legal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Реквизиты на публичных страницах (политика, правила, поддержка): кто оператор сервиса и как с ним связаться.
 * operator — «ОсОО …» / «ИП …», email и phone — для пользователей и магазинов приложений.
 */
@ConfigurationProperties(prefix = "app.legal")
public record LegalProperties(String operator, String email, String phone, String updated) {
}
