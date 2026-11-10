package fu.mss301.user.infrastructure.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import fu.mss301.user.application.port.in.UserProfileService;
import fu.mss301.user.application.usecase.dto.UserProfileResponse;
import fu.mss301.user.infrastructure.web.controller.UserProfileController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GatewayIdentityWebTest {
    private final UserProfileService profiles = mock(UserProfileService.class);
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final GatewayServiceAuthFilter filter = new GatewayServiceAuthFilter(SECRET);

    @Test
    void forgedIdentityCannotReachProfileController() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new UserProfileController(profiles))
                .addFilters(filter).build();
        mvc.perform(get("/api/users/me").header("X-User-Id", "42").header("X-User-Roles", "ADMIN"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void missingIdentityCannotReachProfileController() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new UserProfileController(profiles))
                .addFilters(filter).build();

        mvc.perform(get("/api/users/me").header("X-Gateway-Key", SECRET))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void forwardedIdentityReachesProfileControllerWithGatewayKey() throws Exception {
        when(profiles.me(42)).thenReturn(new UserProfileResponse(
                42, "Customer", "customer@example.com", "0912345678", null, null,
                "Ha Noi", null, null, null, null));
        var mvc = MockMvcBuilders.standaloneSetup(new UserProfileController(profiles))
                .addFilters(filter).build();

        mvc.perform(get("/api/users/me")
                        .header("X-Gateway-Key", SECRET)
                        .header("X-User-Id", "42")
                        .header("X-User-Roles", "CUSTOMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(42));
        verify(profiles).me(42);
    }
}
