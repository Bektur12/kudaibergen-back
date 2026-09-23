package kg.kudaibergen.auth.dto;

import kg.kudaibergen.user.entity.UserRole;

public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds,
                            boolean isNewUser, UserRole role) {
}
