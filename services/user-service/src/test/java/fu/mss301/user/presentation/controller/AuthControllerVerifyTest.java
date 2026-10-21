package fu.mss301.user.presentation.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.mss301.common.exception.GlobalExceptionHandler;
import fu.mss301.user.application.dto.RegisterResponse;
import fu.mss301.user.application.dto.VerifyRegistrationRequest;
import fu.mss301.user.application.port.in.LoginService;
import fu.mss301.user.application.port.in.RefreshService;
import fu.mss301.user.application.port.in.RegistrationService;
import fu.mss301.user.application.port.in.RegistrationVerificationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerVerifyTest {
    private final RegistrationVerificationService verification = mock(RegistrationVerificationService.class);
    private final AuthController controller = new AuthController(mock(LoginService.class), mock(RefreshService.class),
            mock(RegistrationService.class), verification);
    private final UUID id = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");

    @Test
    void validRequestReturnsCreatedUser() throws Exception {
        when(verification.verify(new VerifyRegistrationRequest(id, "012345")))
                .thenReturn(new RegisterResponse(42, "Customer", "customer@example.com", null, "ACTIVE"));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/auth/register/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationId\":\"" + id + "\",\"otp\":\"012345\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void malformedOtpReturnsBadRequestBeforeService() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/register/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationId\":\"" + id + "\",\"otp\":\"12345\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verification);
    }
}
