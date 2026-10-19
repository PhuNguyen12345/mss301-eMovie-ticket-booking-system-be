package fu.mss301.user.presentation.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.mss301.common.exception.GlobalExceptionHandler;
import fu.mss301.user.application.dto.PasswordChangeResponse;
import fu.mss301.user.application.dto.ResetPasswordRequest;
import fu.mss301.user.application.port.in.PasswordResetService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PasswordResetControllerTest {
    private final PasswordResetService passwords = mock(PasswordResetService.class);

    @Test
    void forgotReturnsGenericAcceptedResponseWithoutAuthentication() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PasswordResetController(passwords))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void resetReturnsSuccessWithoutPasswordInResponse() throws Exception {
        when(passwords.reset(new ResetPasswordRequest("person@example.com", "012345", "NewSecret123!")))
                .thenReturn(new PasswordChangeResponse(Instant.parse("2026-10-14T07:00:00Z")));
        var mvc = MockMvcBuilders.standaloneSetup(new PasswordResetController(passwords))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"otp\":\"012345\","
                                + "\"newPassword\":\"NewSecret123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changedAt").exists())
                .andExpect(jsonPath("$.data.newPassword").doesNotExist());
    }

    @Test
    void invalidOtpShapeIsRejectedBeforeService() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PasswordResetController(passwords))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"person@example.com\",\"otp\":\"12345\","
                                + "\"newPassword\":\"NewSecret123!\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(passwords);
    }
}
