package fu.mss301.user.infrastructure.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.mss301.common.exception.GlobalExceptionHandler;
import fu.mss301.user.application.port.in.LoginService;
import fu.mss301.user.application.port.in.RefreshService;
import fu.mss301.user.application.port.in.RefreshTokenService;
import fu.mss301.user.application.usecase.StatusCode;
import fu.mss301.user.application.usecase.UseCaseException;
import fu.mss301.user.application.usecase.dto.LoginResponse;
import fu.mss301.user.application.usecase.dto.RefreshRequest;
import fu.mss301.user.domain.model.RoleName;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BrowserSessionControllerTest {
    private final LoginService login = mock(LoginService.class);
    private final RefreshService refresh = mock(RefreshService.class);
    private final RefreshTokenService tokens = mock(RefreshTokenService.class);
    private final LoginResponse result = new LoginResponse("access", "Bearer", 900, "rotated-secret", 2592000,
            new LoginResponse.LoginUserResponse(1, "Admin", "admin@example.test", Set.of(RoleName.ADMIN)));

    private MockMvc mvc(boolean secure) {
        return MockMvcBuilders.standaloneSetup(new BrowserSessionController(login, refresh, tokens, secure))
                .setControllerAdvice(new UseCaseExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    @Test
    void loginKeepsRefreshCredentialInSecureHttpOnlyCookie() throws Exception {
        when(login.login(any())).thenReturn(result);
        mvc(true).perform(post("/api/auth/login?transport=cookie").contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie(BrowserSessionController.COOKIE, "old-secret"))
                .content("{\"email\":\"admin@example.test\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=2592000")));
        verify(tokens).revoke("old-secret");
    }

    @Test
    void refreshRotatesCookieWithoutReadingTokenFromBody() throws Exception {
        when(refresh.refresh(new RefreshRequest("cookie-secret"))).thenReturn(result);
        mvc(false).perform(post("/api/auth/refresh?transport=cookie").contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie(BrowserSessionController.COOKIE, "cookie-secret"))
                .content("{\"refreshToken\":\"ignored-body\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString("emovie_refresh=rotated-secret")))
                .andExpect(header().string("Set-Cookie", not(containsString("Secure"))));
        verify(refresh).refresh(new RefreshRequest("cookie-secret"));
    }

    @Test
    void missingOrRevokedCookieDoesNotRestoreSession() throws Exception {
        var mvc = mvc(true);
        mvc.perform(post("/api/auth/refresh?transport=cookie").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(refresh);
        when(refresh.refresh(any())).thenThrow(new UseCaseException(StatusCode.UNAUTHORIZED, "Invalid refresh token"));
        mvc.perform(post("/api/auth/refresh?transport=cookie").contentType(MediaType.APPLICATION_JSON).content("{}")
                .cookie(new Cookie(BrowserSessionController.COOKIE, "revoked")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesCredentialAndExpiresCookie() throws Exception {
        mvc(true).perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content("{}")
                .cookie(new Cookie(BrowserSessionController.COOKIE, "current")))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")));
        verify(tokens).revoke("current");
    }

    @Test
    void formAndPlainTextRequestsCannotUseCookieEndpoints() throws Exception {
        for (var type : new MediaType[] { MediaType.APPLICATION_FORM_URLENCODED, MediaType.TEXT_PLAIN }) {
            for (var path : new String[] { "login?transport=cookie", "refresh?transport=cookie", "logout" }) {
                mvc(true).perform(post("/api/auth/" + path).contentType(type).content("{}"))
                        .andExpect(status().isUnsupportedMediaType());
            }
        }
        verifyNoInteractions(login, refresh, tokens);
    }
}
