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
public class PasswordOtp {
    private Integer id;
    private Integer userId;
    @ToString.Exclude private String otpHash;
    private PasswordOtpPurpose purpose;
    @Builder.Default private Integer failedAttempts = 0;
    @Builder.Default private OtpDeliveryStatus otpDeliveryStatus = OtpDeliveryStatus.PENDING;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
    private Long version;
    public OtpState verificationState(LocalDateTime now, int maxAttempts) {
        if (usedAt != null)
            return OtpState.USED;
        if (!expiresAt.isAfter(now))
            return OtpState.EXPIRED;
        if (failedAttempts >= maxAttempts)
            return OtpState.ATTEMPTS_EXHAUSTED;
        return OtpState.READY;
    }
    public long retryAfter(LocalDateTime now, java.time.Duration cooldown) {
        return OtpState.remainingSeconds(now, createdAt.plus(cooldown));
    }
    public int recordInvalidAttempt(LocalDateTime now, int maxAttempts) {
        if (verificationState(now, maxAttempts) != OtpState.READY)
            throw new fu.mss301.user.domain.exception.DomainException(
                    "OTP is not available for verification");
        return ++failedAttempts;
    }
    public void consume(LocalDateTime now, int maxAttempts) {
        if (verificationState(now, maxAttempts) != OtpState.READY)
            throw new fu.mss301.user.domain.exception.DomainException(
                    "OTP is not available for verification");
        usedAt = now;
    }
    public void invalidate(LocalDateTime now) {
        if (usedAt == null)
            usedAt = now;
    }
    public void deliveryFailed() {
        otpDeliveryStatus = OtpDeliveryStatus.FAILED;
    }
}
