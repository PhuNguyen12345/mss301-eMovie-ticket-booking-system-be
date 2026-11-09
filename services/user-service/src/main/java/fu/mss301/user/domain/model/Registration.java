package fu.mss301.user.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@ToString
public class Registration {
    private String id;
    private String fullName;
    private String email;
    private String phone;
    @ToString.Exclude private String passwordHash;
    @ToString.Exclude private String otpHash;
    private LocalDateTime expiresAt;
    private LocalDateTime nextOtpSendAt;
    @Builder.Default private Integer failedAttempts = 0;
    @Builder.Default private Integer resendCount = 0;
    @Builder.Default private OtpDeliveryStatus otpDeliveryStatus = OtpDeliveryStatus.PENDING;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;
    public boolean blocksNewRegistration(LocalDateTime now) {
        return confirmedAt != null || expiresAt.isAfter(now);
    }
    public OtpState verificationState(LocalDateTime now, int maxAttempts) {
        if (confirmedAt != null)
            return OtpState.USED;
        if (!expiresAt.isAfter(now))
            return OtpState.EXPIRED;
        if (failedAttempts >= maxAttempts)
            return OtpState.ATTEMPTS_EXHAUSTED;
        return OtpState.READY;
    }
    public OtpState resendState(LocalDateTime now, int maxResends) {
        if (confirmedAt != null)
            return OtpState.USED;
        if (resendCount >= maxResends)
            return OtpState.RESEND_LIMIT_REACHED;
        if (nextOtpSendAt.isAfter(now))
            return OtpState.COOLDOWN;
        return OtpState.READY;
    }
    public long retryAfter(LocalDateTime now) {
        return OtpState.remainingSeconds(now, nextOtpSendAt);
    }
    public int recordInvalidAttempt(LocalDateTime now, int maxAttempts) {
        if (verificationState(now, maxAttempts) != OtpState.READY)
            throw new fu.mss301.user.domain.exception.DomainException(
                    "OTP is not available for verification");
        updatedAt = now;
        return ++failedAttempts;
    }
    public void confirm(LocalDateTime now, int maxAttempts) {
        if (verificationState(now, maxAttempts) != OtpState.READY)
            throw new fu.mss301.user.domain.exception.DomainException("Registration cannot be confirmed");
        confirmedAt = now;
        updatedAt = now;
    }
    public void resend(String hash, LocalDateTime now, java.time.Duration ttl, java.time.Duration cooldown,
            int maxResends) {
        if (resendState(now, maxResends) != OtpState.READY)
            throw new fu.mss301.user.domain.exception.DomainException("Registration OTP cannot be resent");
        if (hash == null || hash.isBlank() || ttl.isNegative() || ttl.isZero() || cooldown.isNegative()
                || cooldown.isZero())
            throw new fu.mss301.user.domain.exception.DomainException(
                    "OTP hash and positive durations are required");
        otpHash = hash;
        expiresAt = now.plus(ttl);
        nextOtpSendAt = now.plus(cooldown);
        failedAttempts = 0;
        resendCount++;
        otpDeliveryStatus = OtpDeliveryStatus.PENDING;
        updatedAt = now;
    }
}
