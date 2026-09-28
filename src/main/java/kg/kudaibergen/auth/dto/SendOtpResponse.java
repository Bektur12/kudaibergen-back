package kg.kudaibergen.auth.dto;

/**
 * expiresIn — сколько живёт код, resendIn — через сколько секунд доступна «Отправить снова» (экран 02).
 * debugCode заполняется только в dev-режиме (app.otp.expose-code=true).
 */
public record SendOtpResponse(long expiresIn, long resendIn, String debugCode) {
}
