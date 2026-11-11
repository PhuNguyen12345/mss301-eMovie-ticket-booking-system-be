package fu.mss301.user.infrastructure.web.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.mss301.common.exception.GlobalExceptionHandler;
import fu.mss301.user.infrastructure.config.GatewayServiceAuthFilter;
import fu.mss301.user.application.usecase.dto.UserProfileResponse;
import fu.mss301.user.application.port.in.UserProfileService;
import fu.mss301.user.application.usecase.dto.UserSummary;
import fu.mss301.user.application.usecase.UseCaseException;
import fu.mss301.user.application.usecase.StatusCode;
import java.util.List;
import java.util.Set;
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

        mvc.perform(get("/api/users/me").requestAttr(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE, 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.email").value("customer@example.com"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.roles").doesNotExist());
    }


    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new UserProfileController(profiles))
                .setControllerAdvice(new UseCaseExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    @Test
    void adminsReadCustomerSummariesWithoutPrivateFields() throws Exception {
        when(profiles.summaries(List.of(73, 74))).thenReturn(List.of(
                new UserSummary(73, "Nguyễn Văn An", "an@example.com", "0901234561", "ACTIVE")));

        mvc().perform(get("/api/users").param("ids", "73,74")
                        .requestAttr(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE, 1)
                        .requestAttr(GatewayServiceAuthFilter.USER_ROLES_ATTRIBUTE, Set.of("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(73))
                .andExpect(jsonPath("$.data[0].fullName").value("Nguyễn Văn An"))
                .andExpect(jsonPath("$.data[0].email").value("an@example.com"))
                .andExpect(jsonPath("$.data[0].phone").value("0901234561"))
                .andExpect(jsonPath("$.data[0].dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.data[0].address").doesNotExist())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
    }

    @Test
    void customersCannotLookUpOtherUsers() throws Exception {
        mvc().perform(get("/api/users").param("ids", "73")
                        .requestAttr(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE, 73)
                        .requestAttr(GatewayServiceAuthFilter.USER_ROLES_ATTRIBUTE, Set.of("CUSTOMER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }

    @Test
    void invalidIdsAre400() throws Exception {
        when(profiles.summaries(List.of(0))).thenThrow(new UseCaseException(StatusCode.BAD_REQUEST, "User ids must be positive"));
        mvc().perform(get("/api/users").param("ids", "0")
                        .requestAttr(GatewayServiceAuthFilter.USER_ROLES_ATTRIBUTE, Set.of("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc().perform(get("/api/users").param("ids", "abc")
                        .requestAttr(GatewayServiceAuthFilter.USER_ROLES_ATTRIBUTE, Set.of("ADMIN")))
                .andExpect(status().isBadRequest());
    }
}
