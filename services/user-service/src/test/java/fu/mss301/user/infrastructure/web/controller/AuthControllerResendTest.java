package fu.mss301.user.infrastructure.web.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fu.mss301.common.exception.GlobalExceptionHandler;
import fu.mss301.user.application.usecase.StatusCode;
import fu.mss301.user.application.usecase.UseCaseException;
import fu.mss301.user.application.usecase.dto.ResendRegistrationOtpRequest;
import fu.mss301.user.application.usecase.dto.RegistrationOtpResponse;
import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.application.port.in.LoginService;
import fu.mss301.user.application.port.in.RefreshService;
import fu.mss301.user.application.port.in.RegistrationService;
import fu.mss301.user.application.port.in.RegistrationVerificationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
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
        when(registration.resendOtp(new ResendRegistrationOtpRequest(ID)))
                .thenThrow(new UseCaseException(StatusCode.TOO_MANY_REQUESTS, "Please wait", 31L));
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new UseCaseExceptionHandler(), new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/v1/auth/register/resend-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationId\":\"" + ID + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
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
