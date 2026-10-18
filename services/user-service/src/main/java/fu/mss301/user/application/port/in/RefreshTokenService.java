package fu.mss301.user.application.port.in;

import fu.mss301.user.application.dto.LoginResponse.LoginUserResponse;

public interface RefreshTokenService {
    IssuedRefreshToken issue(Integer userId);
    RotatedRefreshToken rotate(String rawToken);

    public record IssuedRefreshToken(String token, long expiresIn) {
        @Override
        public String toString() {
            return "IssuedRefreshToken[token=[REDACTED], expiresIn=" + expiresIn + "]";
        }
    }

    public record RotatedRefreshToken(LoginUserResponse user, IssuedRefreshToken refreshToken) {
    }
}
