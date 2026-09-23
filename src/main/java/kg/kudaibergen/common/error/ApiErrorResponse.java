package kg.kudaibergen.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Единый формат ошибки: { "code": ..., "message": ..., "field": null }. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiErrorResponse(String code, String message, String field) {
}
