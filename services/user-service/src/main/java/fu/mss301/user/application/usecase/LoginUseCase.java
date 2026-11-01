package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.*;

import fu.mss301.user.application.usecase.*;
import fu.mss301.user.application.usecase.dto.LoginRequest;
import fu.mss301.user.application.usecase.dto.LoginResponse;
import fu.mss301.user.domain.repository.UserStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class LoginUseCase implements LoginService {
    private final UserAuthenticationService authentication;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final UserStore repository;
    private final Clock clock;

    public LoginUseCase(UserAuthenticationService authentication, JwtService jwtService,
                        RefreshTokenService refreshTokens, UserStore repository, Clock clock) {
        this.authentication = authentication;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.repository = repository;
        this.clock = clock;
    }

    public LoginResponse login(LoginRequest request) {
        var user = authentication.authenticate(request);
        var response = jwtService.issue(user);
        int updated = repository.updateLastLoginAt(user.userId(),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        if (updated != 1) {
            throw new IllegalStateException("Unable to update login information");
        }
        var refreshToken = refreshTokens.issue(user.userId());
        return response.withRefreshToken(refreshToken.token(), refreshToken.expiresIn());
    }
}
