package fu.mss301.user.presentation.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.mss301.common.exception.GlobalExceptionHandler;
import fu.mss301.user.presentation.security.GatewayServiceAuthFilter;
import fu.mss301.user.application.dto.UserProfileResponse;
import fu.mss301.user.application.port.in.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UserProfileControllerTest {
    private final UserProfileService profiles = mock(UserProfileService.class);

    @Test
    void returnsProfileWithoutSensitiveFields() throws Exception {
        when(profiles.me(42)).thenReturn(new UserProfileResponse(
                42, "Customer", "customer@example.com", "0912345678", null, null,
                "Ha Noi", null, null, null, null));
        var mvc = MockMvcBuilders.standaloneSetup(new UserProfileController(profiles))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/users/me").requestAttr(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE, 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.email").value("customer@example.com"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.roles").doesNotExist());
    }

}
