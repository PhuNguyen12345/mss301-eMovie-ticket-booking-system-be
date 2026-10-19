package fu.mss301.user.presentation.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.mss301.common.exception.GlobalExceptionHandler;
import fu.mss301.user.application.dto.LoginRequest;
import fu.mss301.user.application.dto.LoginResponse;
import fu.mss301.user.application.dto.LoginResponse.LoginUserResponse;
import fu.mss301.user.application.dto.RefreshRequest;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.application.port.in.LoginService;
import fu.mss301.user.application.port.in.RegistrationService;
import fu.mss301.user.application.port.in.RegistrationVerificationService;
import fu.mss301.user.application.port.in.RefreshService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerLoginTest {
    private final LoginService login = mock(LoginService.class);
    private final RefreshService refresh = mock(RefreshService.class);

    @Test
    void returnsApiResponseWithAccessToken() throws Exception {
        var user = new LoginUserResponse(1, "Admin", "admin@gmail.com", Set.of(RoleName.ADMIN));
        when(login.login(new LoginRequest("admin@gmail.com", "1-8")))
                .thenReturn(new LoginResponse("signed-token", "Bearer", 900, "refresh-token", 2592000, user));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(login, refresh,
                mock(RegistrationService.class), mock(RegistrationVerificationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" Admin@Gmail.com \",\"password\":\"1-8\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void invalidBodyReturns400BeforeLogin() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(login, refresh,
                mock(RegistrationService.class), mock(RegistrationVerificationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(login);
    }

    @Test
    void refreshReturnsRotatedTokens() throws Exception {
        var user = new LoginUserResponse(1, "Admin", "admin@gmail.com", Set.of(RoleName.ADMIN));
        when(refresh.refresh(new RefreshRequest("old-refresh-token")))
                .thenReturn(new LoginResponse("new-access", "Bearer", 900, "new-refresh", 2592000, user));
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(login, refresh,
                mock(RegistrationService.class), mock(RegistrationVerificationService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.data.refreshExpiresIn").value(2592000));
    }
}
