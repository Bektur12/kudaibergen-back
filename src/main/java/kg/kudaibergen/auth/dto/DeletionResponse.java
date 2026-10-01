package kg.kudaibergen.auth.dto;

import java.time.Instant;

/** purgeAt — когда данные будут стёрты; вход до этого момента отменяет удаление. */
public record DeletionResponse(Instant purgeAt) {
}
