package kg.kudaibergen.request.dto;

import java.time.Instant;

/** «Отправить всему рынку» (20, 32): сколько боксов добавилось, сколько всего получили, новый срок. */
public record WidenResultDto(int recipientsAdded, int recipientsCount, Instant expiresAt) {
}
