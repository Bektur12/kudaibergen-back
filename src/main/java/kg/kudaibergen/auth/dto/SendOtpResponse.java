package kg.kudaibergen.auth.dto;

import org.springframework.lang.Nullable;

/**
 * expiresIn — сколько живёт код, resendIn — через сколько секунд доступна «Отправить снова» (экран 02).
 * debugCode заполняется только в dev-режиме (app.otp.expose-code=true).
 */
public record SendOtpResponse(long expiresIn, long resendIn, @Nullable String debugCode) {
}
