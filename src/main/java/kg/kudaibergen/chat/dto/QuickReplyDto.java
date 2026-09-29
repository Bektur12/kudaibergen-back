package kg.kudaibergen.chat.dto;

import kg.kudaibergen.chat.entity.QuickReply;
import org.springframework.lang.Nullable;

/**
 * Кнопка над полем ввода. code != null — быстрый ответ из ТЗ: MESSAGE отправляется как
 * {quickReply: code}, ACTION выполняет клиент (маршрут, закрытие запроса). templateId != null — свой
 * шаблон магазина: text подставляется в поле ввода и уходит обычным сообщением.
 */
public record QuickReplyDto(@Nullable QuickReply code, QuickReply.QuickReplyKind kind, @Nullable Long templateId, String text) {
}
