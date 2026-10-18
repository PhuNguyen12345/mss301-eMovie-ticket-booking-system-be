package fu.mss301.user.application.dto;

import fu.mss301.user.domain.model.RoleName;
import java.util.Set;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn,
        LoginUserResponse user
) {
    public LoginResponse withRefreshToken(String token, long ttlSeconds) {
        return new LoginResponse(accessToken, tokenType, expiresIn, token, ttlSeconds, user);
    }

    public record LoginUserResponse(
            Integer userId,
            String fullName,
            String email,
            Set<RoleName> roles
    ) {
    }

    @Override
    public String toString() {
        return "LoginResponse[accessToken=[REDACTED], tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + ", refreshToken=[REDACTED], refreshExpiresIn="
                + refreshExpiresIn + ", user=" + user + "]";
    }
}
