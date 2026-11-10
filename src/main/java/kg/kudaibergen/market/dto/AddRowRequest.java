package kg.kudaibergen.market.dto;

/**
 * Продавец не нашёл ряд в списке (10а): код, как он его ввёл («Ф», «ряд ф»). Нормализуется и проверяется
 * на сервере — пустой или недопустимый код даёт 400 BAD_ROW_CODE.
 */
public record AddRowRequest(String code) {
}
