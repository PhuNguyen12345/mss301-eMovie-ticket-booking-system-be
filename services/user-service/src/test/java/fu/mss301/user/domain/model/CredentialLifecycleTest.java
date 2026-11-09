package fu.mss301.user.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import fu.mss301.user.domain.exception.DomainException;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CredentialLifecycleTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 12, 0);

    @Test
    void otpExpiresAtBoundaryAndCannotBeReplayed() {
        PasswordOtp expired = PasswordOtp.builder().expiresAt(NOW).build();
        assertEquals(OtpState.EXPIRED, expired.verificationState(NOW, 5));
        assertThrows(DomainException.class, () -> expired.consume(NOW, 5));
        PasswordOtp valid = PasswordOtp.builder().expiresAt(NOW.plusMinutes(1)).build();
        valid.consume(NOW, 5);
        assertEquals(OtpState.USED, valid.verificationState(NOW, 5));
        assertThrows(DomainException.class, () -> valid.consume(NOW, 5));
    }

    @Test
    void attemptLimitStopsFurtherMutations() {
        PasswordOtp otp = PasswordOtp.builder().expiresAt(NOW.plusMinutes(1)).failedAttempts(4).build();
        assertEquals(5, otp.recordInvalidAttempt(NOW, 5));
        assertEquals(OtpState.ATTEMPTS_EXHAUSTED, otp.verificationState(NOW, 5));
        assertThrows(DomainException.class, () -> otp.recordInvalidAttempt(NOW, 5));
        assertEquals(5, otp.getFailedAttempts());
    }

    @Test
    void registrationResendEnforcesCooldownAndResetsAttemptBudget() {
        Registration registration = Registration.builder().expiresAt(NOW).nextOtpSendAt(NOW.plusNanos(1))
                .failedAttempts(5).otpHash("old").build();
        assertEquals(1, registration.retryAfter(NOW));
        assertThrows(DomainException.class, () -> registration.resend("new", NOW,
                Duration.ofMinutes(5), Duration.ofSeconds(60), 5));
        registration.resend("new", NOW.plusNanos(1), Duration.ofMinutes(5), Duration.ofSeconds(60), 5);
        assertEquals(0, registration.getFailedAttempts());
        assertEquals(1, registration.getResendCount());
        assertEquals("new", registration.getOtpHash());
        assertEquals(OtpState.READY, registration.verificationState(NOW.plusSeconds(1), 5));
        registration.confirm(NOW.plusSeconds(1), 5);
        assertEquals(OtpState.USED, registration.resendState(NOW.plusMinutes(2), 5));
    }

    @Test
    void refreshRotationIsSingleUseAndRequiresActiveAccount() {
        User user = User.builder().status("ACTIVE").emailVerifiedAt(NOW).build();
        RefreshCredential token = RefreshCredential.builder().user(user).expiresAt(NOW.plusMinutes(1)).build();
        token.consume(NOW);
        assertFalse(token.canRotate(NOW));
        assertThrows(DomainException.class, () -> token.consume(NOW));
        assertFalse(token.toBuilder().revoked(false).expiresAt(NOW).build().canRotate(NOW));
        assertFalse(token.toBuilder().revoked(false).user(user.toBuilder().status("INACTIVE").build())
                .build().canRotate(NOW));
    }

    @Test
    void logoutRevokesExpiredCredentialsAndPreservesOriginalRevocationTime() {
        User user = User.builder().status("INACTIVE").build();
        RefreshCredential token = RefreshCredential.builder().user(user).expiresAt(NOW.minusMinutes(1)).build();
        token.revoke(NOW);
        token.revoke(NOW.plusMinutes(1));
        assertTrue(token.getRevoked());
        assertEquals(NOW, token.getRevokedAt());
        assertEquals(NOW, token.getUpdatedAt());
        assertFalse(token.canRotate(NOW));
    }

    @Test
    void inactiveAccountCannotChangePassword() {
        User user = User.builder().status("INACTIVE").passwordHash("old").build();
        assertThrows(DomainException.class, () -> user.changePassword("new", NOW));
        assertEquals("old", user.getPasswordHash());
    }
}
