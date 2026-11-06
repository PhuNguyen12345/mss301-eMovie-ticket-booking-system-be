package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.*;

import fu.mss301.user.application.usecase.*;
import fu.mss301.user.application.usecase.dto.LoginResponse;
import fu.mss301.user.application.usecase.dto.RefreshRequest;

public class RefreshUseCase implements RefreshService {
    private final RefreshTokenService refreshTokens;
    private final JwtService jwtService;

    public RefreshUseCase(RefreshTokenService refreshTokens, JwtService jwtService) {
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
    }

    public LoginResponse refresh(RefreshRequest request) {
        var rotated = refreshTokens.rotate(request.refreshToken());
        return jwtService.issue(rotated.user())
                .withRefreshToken(rotated.refreshToken().token(), rotated.refreshToken().expiresIn());
    }
}
