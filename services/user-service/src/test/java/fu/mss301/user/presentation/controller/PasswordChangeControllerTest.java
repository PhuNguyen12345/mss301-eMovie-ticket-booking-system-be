package fu.mss301.user.presentation.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.mss301.common.exception.GlobalExceptionHandler;
import fu.mss301.user.presentation.security.GatewayServiceAuthFilter;
import fu.mss301.user.application.dto.ConfirmPasswordChangeRequest;
import fu.mss301.user.application.dto.PasswordChangeOtpRequest;
import fu.mss301.user.application.dto.PasswordChangeOtpResponse;
import fu.mss301.user.application.dto.PasswordChangeResponse;
import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.application.port.in.PasswordChangeService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PasswordChangeControllerTest {
    private final PasswordChangeService passwords = mock(PasswordChangeService.class);

    @Test
    void otpRequestReturnsAcceptedWithoutExposingCode() throws Exception {
        when(passwords.requestOtp(7, new PasswordChangeOtpRequest("OldSecret123!")))
                .thenReturn(new PasswordChangeOtpResponse(Instant.parse("2026-10-14T07:05:00Z"),
                        Instant.parse("2026-10-14T07:01:00Z"), OtpDeliveryStatus.PENDING));
        var mvc = MockMvcBuilders.standaloneSetup(new PasswordChangeController(passwords))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/users/me/password/otp")
                        .requestAttr(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"OldSecret123!\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.otpDeliveryStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.otp").doesNotExist());
    }

    @Test
    void confirmReturnsChangedAt() throws Exception {
        when(passwords.confirm(7, new ConfirmPasswordChangeRequest("012345", "NewSecret123!")))
                .thenReturn(new PasswordChangeResponse(Instant.parse("2026-10-14T07:00:00Z")));
        var mvc = MockMvcBuilders.standaloneSetup(new PasswordChangeController(passwords))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/users/me/password/confirm")
                        .requestAttr(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"012345\",\"newPassword\":\"NewSecret123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changedAt").exists())
                .andExpect(jsonPath("$.data.newPassword").doesNotExist());
    }

    @Test
    void malformedOtpIsRejectedBeforeService() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PasswordChangeController(passwords))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/users/me/password/confirm")
                        .requestAttr(GatewayServiceAuthFilter.USER_ID_ATTRIBUTE, 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"12345\",\"newPassword\":\"NewSecret123!\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(passwords);
    }
}
