package fu.mss301.user.presentation.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.mss301.common.exception.ApiException;
import fu.mss301.common.exception.GlobalExceptionHandler;
import fu.mss301.user.application.dto.ResendRegistrationOtpRequest;
import fu.mss301.user.application.dto.RegistrationOtpResponse;
import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.application.port.in.LoginService;
import fu.mss301.user.application.port.in.RefreshService;
import fu.mss301.user.application.port.in.RegistrationService;
import fu.mss301.user.application.port.in.RegistrationVerificationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerResendTest {
    private static final UUID ID = UUID.fromString("16c98f29-3b38-480f-b969-72eaf6c1ae09");
    private final RegistrationService registration = mock(RegistrationService.class);
    private final AuthController controller = new AuthController(mock(LoginService.class), mock(RefreshService.class),
            registration, mock(RegistrationVerificationService.class));

    @Test
    void acceptedResponseContainsNewOtpTimesAndSameRegistrationId() throws Exception {
        when(registration.resendOtp(new ResendRegistrationOtpRequest(ID)))
                .thenReturn(new RegistrationOtpResponse(ID.toString(),
                        Instant.parse("2026-10-14T07:05:00Z"), Instant.parse("2026-10-14T07:01:00Z"),
                        OtpDeliveryStatus.PENDING));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/auth/register/resend-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationId\":\"" + ID + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(202))
                .andExpect(jsonPath("$.data.registrationId").value(ID.toString()))
                .andExpect(jsonPath("$.data.otpDeliveryStatus").value("PENDING"));
    }

    @Test
    void cooldownResponseContainsRetryAfterHeader() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, "31");
        when(registration.resendOtp(new ResendRegistrationOtpRequest(ID)))
                .thenThrow(new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait", headers));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/auth/register/resend-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationId\":\"" + ID + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "31"));
    }

    @Test
    void invalidRegistrationIdReturnsBadRequestBeforeService() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/auth/register/resend-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationId\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(registration);
    }
}
