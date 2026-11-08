package fu.mss301.user.infrastructure.web.controller;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.user.application.port.in.LoginService;
import fu.mss301.user.application.port.in.RefreshService;
import fu.mss301.user.application.port.in.RefreshTokenService;
import fu.mss301.user.application.usecase.StatusCode;
import fu.mss301.user.application.usecase.UseCaseException;
import fu.mss301.user.application.usecase.dto.RefreshRequest;
import fu.mss301.user.infrastructure.web.dto.ApiDtoMapper;
import fu.mss301.user.infrastructure.web.dto.LoginRequest;
import fu.mss301.user.infrastructure.web.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class BrowserSessionController {
    public static final String COOKIE = "emovie_refresh";
    private final LoginService login;
    private final RefreshService refresh;
    private final RefreshTokenService tokens;
    private final boolean secure;

    public BrowserSessionController(LoginService login, RefreshService refresh, RefreshTokenService tokens,
            @Value("${session.cookie.secure:true}") boolean secure) {
        this.login = login;
        this.refresh = refresh;
        this.tokens = tokens;
        this.secure = secure;
    }

    // JSON-only POSTs require a CORS preflight across origins; the cookie is also SameSite=Strict.
    @PostMapping(value = "/login", params = "transport=cookie", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
            @CookieValue(name = COOKIE, required = false) String previous) {
        var result = ApiDtoMapper.toApi(login.login(ApiDtoMapper.toApplication(request)));
        tokens.revoke(previous);
        return authenticated(result);
    }

    @PostMapping(value = "/refresh", params = "transport=cookie", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = COOKIE, required = false) String credential) {
        if (credential == null) throw new UseCaseException(StatusCode.UNAUTHORIZED, "No browser session");
        return authenticated(ApiDtoMapper.toApi(refresh.refresh(new RefreshRequest(credential))));
    }

    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = COOKIE, required = false) String credential) {
        tokens.revoke(credential);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", 0))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(null, "Browser session ended"));
    }

    private ResponseEntity<ApiResponse<LoginResponse>> authenticated(LoginResponse result) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), result.refreshExpiresIn()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(result.withRefreshToken(null, result.refreshExpiresIn()), "Session authenticated"));
    }

    private String cookie(String value, long ttl) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure).sameSite("Strict")
                .path("/api/auth").maxAge(ttl).build().toString();
    }
}
