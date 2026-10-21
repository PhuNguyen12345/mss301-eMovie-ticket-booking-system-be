package fu.mss301.user.application.service;

import fu.mss301.user.application.dto.ForgotPasswordRequest;
import fu.mss301.common.exception.ApiException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.mss301.user.application.dto.ResetPasswordRequest;
import fu.mss301.user.domain.model.PasswordOtpPurpose;
import fu.mss301.user.domain.model.PasswordOtp;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.infrastructure.notification.OtpGenerator;
import fu.mss301.user.application.event.PasswordResetOtpRequested;
import fu.mss301.user.application.port.out.PasswordOtpStore;
import fu.mss301.user.application.port.out.RefreshTokenStore;
import fu.mss301.user.application.port.out.UserStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import fu.mss301.user.application.port.out.EventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import fu.mss301.user.infrastructure.security.SpringPasswordHasher;

class PasswordResetServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-14T07:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private final UserStore users = mock(UserStore.class);
    private final PasswordOtpStore otps = mock(PasswordOtpStore.class);
    private final RefreshTokenStore refreshTokens = mock(RefreshTokenStore.class);
    private final OtpGenerator generator = mock(OtpGenerator.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PasswordResetUseCase service = new PasswordResetUseCase(
            users, otps, refreshTokens, new SpringPasswordHasher(encoder), generator, events,
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5), Duration.ofMinutes(1), 5);
    private final User user = User.builder().id(7).email("person@example.com")
            .passwordHash(encoder.encode("OldSecret123!"))
            .status("ACTIVE").emailVerifiedAt(LOCAL_NOW.minusDays(1)).build();

    @Test
    void unknownEmailDoesNotIssueOtp() {
        service.requestOtp(new ForgotPasswordRequest("unknown@example.com"));
        verify(users).findIdByEmail("unknown@example.com");
        verifyNoInteractions(generator, events, otps);
    }

    @Test
    void requestCreatesResetPurposeOtpWithoutExposingRawCode() {
        stubUser();
        when(generator.generate()).thenReturn("012345");
        when(otps.createAndFlush(any())).thenAnswer(invocation -> {
            PasswordOtpStore.NewPasswordOtp draft = invocation.getArgument(0);
            return PasswordOtp.builder().id(12).userId(user.getId()).otpHash(draft.otpHash())
                    .purpose(draft.purpose()).expiresAt(draft.expiresAt())
                    .createdAt(draft.createdAt()).build();
        });

        service.requestOtp(new ForgotPasswordRequest("person@example.com"));

        var saved = ArgumentCaptor.forClass(PasswordOtpStore.NewPasswordOtp.class);
        verify(otps).createAndFlush(saved.capture());
        assertEquals(PasswordOtpPurpose.PASSWORD_RESET, saved.getValue().purpose());
        assertTrue(encoder.matches("012345", saved.getValue().otpHash()));
        var event = ArgumentCaptor.forClass(PasswordResetOtpRequested.class);
        verify(events).publishEvent(event.capture());
        assertEquals(12, event.getValue().otpId());
        assertFalse(event.getValue().toString().contains("012345"));
    }

    @Test
    void cooldownSilentlySkipsAnotherEmail() {
        stubUser();
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(PasswordOtp.builder().createdAt(LOCAL_NOW.minusSeconds(30)).build()));
        service.requestOtp(new ForgotPasswordRequest("person@example.com"));
        verifyNoInteractions(generator, events);
    }

    @Test
    void validResetConsumesOnlyResetOtpAndRevokesRefreshTokens() {
        stubUser();
        PasswordOtp reset = otp(PasswordOtpPurpose.PASSWORD_RESET, "012345");
        PasswordOtp change = otp(PasswordOtpPurpose.PASSWORD_CHANGE, "654321");
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(reset));
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_CHANGE))
                .thenReturn(Optional.of(change));

        var result = service.reset(new ResetPasswordRequest("person@example.com", "012345", "NewSecret123!"));

        assertEquals(NOW, result.changedAt());
        assertTrue(encoder.matches("NewSecret123!", user.getPasswordHash()));
        assertEquals(LOCAL_NOW, reset.getUsedAt());
        assertEquals(LOCAL_NOW, change.getUsedAt());
        verify(refreshTokens).revokeActiveByUserId(7, LOCAL_NOW);
    }

    @Test
    void changePurposeOtpCannotResetPassword() {
        stubUser();
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_CHANGE))
                .thenReturn(Optional.of(otp(PasswordOtpPurpose.PASSWORD_CHANGE, "012345")));
        assertEquals(HttpStatus.BAD_REQUEST, statusFor("012345"));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void fifthWrongOtpAttemptIsPersisted() {
        stubUser();
        PasswordOtp reset = otp(PasswordOtpPurpose.PASSWORD_RESET, "012345");
        reset.setFailedAttempts(4);
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(reset));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusFor("999999"));
        assertEquals(5, reset.getFailedAttempts());
        verify(otps).saveAndFlush(reset);
        verifyNoInteractions(refreshTokens);
    }

    private HttpStatus statusFor(String otp) {
        return assertThrows(ApiException.class, () -> service.reset(
                new ResetPasswordRequest("person@example.com", otp, "NewSecret123!"))).getStatus();
    }

    private void stubUser() {
        when(users.findIdByEmail("person@example.com")).thenReturn(Optional.of(7));
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
    }

    private PasswordOtp otp(PasswordOtpPurpose purpose, String raw) {
        return PasswordOtp.builder().id(12).userId(user.getId()).purpose(purpose).otpHash(encoder.encode(raw))
                .createdAt(LOCAL_NOW.minusSeconds(30)).expiresAt(LOCAL_NOW.plusMinutes(5)).build();
    }
}
