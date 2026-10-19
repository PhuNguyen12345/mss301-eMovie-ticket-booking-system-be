package fu.mss301.user.application.service;

import fu.mss301.user.application.port.in.JwtService;
import fu.mss301.user.application.port.in.LoginService;
import fu.mss301.user.application.port.in.RefreshTokenService;
import fu.mss301.user.application.port.in.UserAuthenticationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.mss301.common.exception.ApiException;
import fu.mss301.user.application.dto.LoginRequest;
import fu.mss301.user.application.dto.LoginResponse;
import fu.mss301.user.application.dto.LoginResponse.LoginUserResponse;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.application.port.out.UserStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LoginServiceTest {
    private final UserAuthenticationService authentication = mock(UserAuthenticationService.class);
    private final JwtService jwt = mock(JwtService.class);
    private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
    private final UserStore repository = mock(UserStore.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T07:00:00Z"), ZoneOffset.UTC);
    private final LoginService service = new LoginUseCase(authentication, jwt, refreshTokens, repository, clock);
    private final LoginRequest request = new LoginRequest("admin@gmail.com", "1-8");

    @Test
    void invalidCredentialsNeverIssueToken() {
        when(authentication.authenticate(request)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        assertThrows(ApiException.class, () -> service.login(request));
        verifyNoInteractions(jwt, refreshTokens, repository);
    }

    @Test
    void successfulLoginIssuesTokenAndRecordsLoginTime() {
        var user = new LoginUserResponse(1, "Admin", "admin@gmail.com", Set.of(RoleName.ADMIN));
        var response = new LoginResponse("token", "Bearer", 900, null, 0, user);
        when(authentication.authenticate(request)).thenReturn(user);
        when(jwt.issue(user)).thenReturn(response);
        when(repository.updateLastLoginAt(1, LocalDateTime.of(2026, 10, 12, 7, 0))).thenReturn(1);
        when(refreshTokens.issue(1)).thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 3600));
        var result = service.login(request);
        assertEquals("token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(3600, result.refreshExpiresIn());
        var order = inOrder(authentication, jwt, repository, refreshTokens);
        order.verify(authentication).authenticate(request);
        order.verify(jwt).issue(user);
        order.verify(repository).updateLastLoginAt(1, LocalDateTime.of(2026, 10, 12, 7, 0));
        order.verify(refreshTokens).issue(1);
    }

    @Test
    void doesNotReturnTokenWhenLoginUpdateFails() {
        var user = new LoginUserResponse(1, "Admin", "admin@gmail.com", Set.of(RoleName.ADMIN));
        when(authentication.authenticate(request)).thenReturn(user);
        when(jwt.issue(user)).thenReturn(new LoginResponse("token", "Bearer", 900, null, 0, user));
        assertThrows(IllegalStateException.class, () -> service.login(request));
        verifyNoInteractions(refreshTokens);
    }
}
