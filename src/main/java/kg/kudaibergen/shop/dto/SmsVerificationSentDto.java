package kg.kudaibergen.shop.dto;

import org.springframework.lang.Nullable;

/** Код ушёл на номер арендатора: sentTo — замаскированный номер, чтобы продавец понял, кому звонить. */
public record SmsVerificationSentDto(String sentTo, long expiresIn, long resendIn, @Nullable String debugCode) {
}
