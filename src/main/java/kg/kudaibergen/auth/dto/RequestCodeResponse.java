package kg.kudaibergen.auth.dto;

/** debugCode заполняется только в dev-режиме (app.sms.expose-code=true). */
public record RequestCodeResponse(long expiresInSeconds, String debugCode) {
}
