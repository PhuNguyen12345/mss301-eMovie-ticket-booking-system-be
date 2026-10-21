package fu.mss301.user.application.service;

import fu.mss301.user.application.dto.PasswordChangeOtpRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import fu.mss301.common.exception.ApiException;
import fu.mss301.user.application.dto.ConfirmPasswordChangeRequest;
import fu.mss301.user.domain.model.PasswordOtp;
import fu.mss301.user.domain.model.PasswordOtpPurpose;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.infrastructure.notification.OtpGenerator;
import fu.mss301.user.application.event.PasswordChangeOtpRequested;
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

class PasswordChangeServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-10-14T07:00:00Z");
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private final UserStore users = mock(UserStore.class);
    private final PasswordOtpStore otps = mock(PasswordOtpStore.class);
    private final RefreshTokenStore refreshTokens = mock(RefreshTokenStore.class);
    private final OtpGenerator generator = mock(OtpGenerator.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final PasswordChangeUseCase service = new PasswordChangeUseCase(
            users, otps, refreshTokens, new SpringPasswordHasher(encoder), generator, events,
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5), Duration.ofMinutes(1), 5);
    private final User user = User.builder().id(7).email("person@example.com")
            .passwordHash(encoder.encode("OldSecret123!"))
            .status("ACTIVE").emailVerifiedAt(LOCAL_NOW.minusDays(1)).build();

    @Test
    void requestOtpStoresHashAndQueuesMail() {
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        when(generator.generate()).thenReturn("012345");
        when(otps.createAndFlush(any())).thenAnswer(invocation -> {
            PasswordOtpStore.NewPasswordOtp draft = invocation.getArgument(0);
            return PasswordOtp.builder().id(12).userId(user.getId()).otpHash(draft.otpHash())
                    .purpose(draft.purpose()).expiresAt(draft.expiresAt())
                    .createdAt(draft.createdAt()).build();
        });

        var response = service.requestOtp(7, new PasswordChangeOtpRequest("OldSecret123!"));

        assertEquals(NOW.plusSeconds(300), response.expiresAt());
        assertEquals(NOW.plusSeconds(60), response.resendAvailableAt());
        var saved = ArgumentCaptor.forClass(PasswordOtpStore.NewPasswordOtp.class);
        verify(otps).createAndFlush(saved.capture());
        assertTrue(encoder.matches("012345", saved.getValue().otpHash()));
        assertFalse(saved.getValue().toString().contains(saved.getValue().otpHash()));
        var event = ArgumentCaptor.forClass(PasswordChangeOtpRequested.class);
        verify(events).publishEvent(event.capture());
        assertEquals(12, event.getValue().otpId());
        assertEquals("012345", event.getValue().otp());
        assertFalse(event.getValue().toString().contains("012345"));
    }

    @Test
    void requestOtpRequiresCurrentPassword() {
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        ApiException error = assertThrows(ApiException.class, () -> service.requestOtp(7, new PasswordChangeOtpRequest("wrong")));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        verifyNoInteractions(generator, otps, events);
    }

    @Test
    void requestOtpRespectsCooldownAndRetryAfter() {
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_CHANGE)).thenReturn(Optional.of(
                PasswordOtp.builder().createdAt(LOCAL_NOW.minusSeconds(30)).build()));

        ApiException error = assertThrows(ApiException.class,
                () -> service.requestOtp(7, new PasswordChangeOtpRequest("OldSecret123!")));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatus());
        assertEquals("30", error.getHeaders().getFirst("Retry-After"));
        verifyNoInteractions(generator, events);
    }

    @Test
    void laterRequestInvalidatesPreviousOtp() {
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        PasswordOtp previous = otp("012345");
        previous.setCreatedAt(LOCAL_NOW.minusSeconds(61));
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_CHANGE)).thenReturn(Optional.of(previous));
        when(generator.generate()).thenReturn("654321");
        when(otps.createAndFlush(any())).thenAnswer(invocation -> {
            PasswordOtpStore.NewPasswordOtp draft = invocation.getArgument(0);
            return PasswordOtp.builder().id(13).userId(user.getId()).otpHash(draft.otpHash())
                    .purpose(draft.purpose()).expiresAt(draft.expiresAt())
                    .createdAt(draft.createdAt()).build();
        });

        service.requestOtp(7, new PasswordChangeOtpRequest("OldSecret123!"));

        assertEquals(LOCAL_NOW, previous.getUsedAt());
        verify(otps).saveAndFlush(previous);
        verify(otps).createAndFlush(any());
    }

    @Test
    void correctOtpChangesPasswordAndRevokesRefreshTokens() {
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        PasswordOtp entry = otp("012345");
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_CHANGE)).thenReturn(Optional.of(entry));

        var result = service.confirm(7, new ConfirmPasswordChangeRequest("012345", "NewSecret123!"));

        assertEquals(NOW, result.changedAt());
        assertTrue(encoder.matches("NewSecret123!", user.getPasswordHash()));
        assertFalse(encoder.matches("OldSecret123!", user.getPasswordHash()));
        assertEquals(LOCAL_NOW, user.getUpdatedAt());
        assertEquals(LOCAL_NOW, entry.getUsedAt());
        verify(users).saveAndFlush(user);
        verify(otps).saveAndFlush(entry);
        verify(refreshTokens).revokeActiveByUserId(7, LOCAL_NOW);
    }

    @Test
    void fifthIncorrectOtpIsSavedAndBlocksFurtherAttempts() {
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        PasswordOtp entry = otp("012345");
        entry.setFailedAttempts(4);
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_CHANGE)).thenReturn(Optional.of(entry));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, statusFor("999999"));
        assertEquals(5, entry.getFailedAttempts());
        verify(otps).saveAndFlush(entry);
        verifyNoInteractions(refreshTokens);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                statusFor("012345"));
        verify(otps, times(1)).saveAndFlush(entry);
    }

    @Test
    void expiredAndUsedOtpCannotChangePassword() {
        when(users.findByIdForUpdate(7)).thenReturn(Optional.of(user));
        PasswordOtp entry = otp("012345");
        when(otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(7, PasswordOtpPurpose.PASSWORD_CHANGE)).thenReturn(Optional.of(entry));
        entry.setExpiresAt(LOCAL_NOW);
        assertEquals(HttpStatus.GONE,
                statusFor("012345"));
        entry.setUsedAt(LOCAL_NOW.minusMinutes(1));
        assertEquals(HttpStatus.CONFLICT,
                statusFor("012345"));
        verifyNoInteractions(refreshTokens);
    }

    private HttpStatus statusFor(String otp) {
        return assertThrows(ApiException.class, () -> service.confirm(7,
                new ConfirmPasswordChangeRequest(otp, "NewSecret123!"))).getStatus();
    }

    private PasswordOtp otp(String raw) {
        return PasswordOtp.builder().id(12).userId(user.getId()).otpHash(encoder.encode(raw))
                .purpose(PasswordOtpPurpose.PASSWORD_CHANGE)
                .createdAt(LOCAL_NOW.minusSeconds(30)).expiresAt(LOCAL_NOW.plusMinutes(5)).build();
    }
}
