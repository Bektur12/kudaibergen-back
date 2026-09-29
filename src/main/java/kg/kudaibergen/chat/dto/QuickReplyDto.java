package kg.kudaibergen.chat.dto;

import kg.kudaibergen.chat.entity.QuickReply;

/**
 * Кнопка над полем ввода. code != null — быстрый ответ из ТЗ: MESSAGE отправляется как
 * {quickReply: code}, ACTION выполняет клиент (маршрут, закрытие запроса). templateId != null — свой
 * шаблон магазина: text подставляется в поле ввода и уходит обычным сообщением.
 */
public record QuickReplyDto(QuickReply code, QuickReply.QuickReplyKind kind, Long templateId, String text) {
}
