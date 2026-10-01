package kg.kudaibergen.auth.dto;

import kg.kudaibergen.user.dto.MeResponse;

/** isNewUser = роль ещё не выбрана: клиент ведёт на экран 03. */
public record TokenResponse(String accessToken, String refreshToken, long expiresIn, boolean isNewUser,
                            MeResponse user) {
}
