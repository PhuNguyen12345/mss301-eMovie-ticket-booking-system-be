package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.*;

import fu.mss301.user.application.usecase.dto.ForgotPasswordRequest;
import fu.mss301.user.application.port.in.PasswordResetService;
import fu.mss301.user.application.usecase.dto.PasswordChangeResponse;
import fu.mss301.user.application.usecase.dto.ResetPasswordRequest;
import fu.mss301.user.domain.model.PasswordOtpPurpose;
import fu.mss301.user.domain.model.PasswordOtp;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.domain.valueobject.Email;
import fu.mss301.user.application.port.out.OtpCodeGenerator;
import fu.mss301.user.domain.event.PasswordResetOtpRequested;
import fu.mss301.user.domain.repository.PasswordOtpStore;
import fu.mss301.user.domain.repository.RefreshTokenStore;
import fu.mss301.user.domain.repository.UserStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import fu.mss301.user.application.port.out.EventPublisher;
import fu.mss301.user.domain.repository.PersistenceConflictException;
import fu.mss301.user.application.port.out.PasswordHasher;

public class PasswordResetUseCase implements PasswordResetService {
    private final UserStore users;
    private final PasswordOtpStore otps;
    private final RefreshTokenStore refreshTokens;
    private final PasswordHasher encoder;
    private final OtpCodeGenerator generator;
    private final EventPublisher events;
    private final Clock clock;
    private final Duration ttl;
    private final Duration cooldown;
    private final int maxAttempts;

    public PasswordResetUseCase(UserStore users, PasswordOtpStore otps,
                                RefreshTokenStore refreshTokens, PasswordHasher encoder,
                                OtpCodeGenerator generator, EventPublisher events, Clock clock,
                                Duration ttl,
                                Duration cooldown,
                                int maxAttempts) {
        if (ttl.isZero() || ttl.isNegative() || cooldown.isZero() || cooldown.isNegative() || maxAttempts < 1) {
            throw new IllegalArgumentException("Password reset OTP limits must be positive");
        }
        this.users = users;
        this.otps = otps;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.generator = generator;
        this.events = events;
        this.clock = clock;
        this.ttl = ttl;
        this.cooldown = cooldown;
        this.maxAttempts = maxAttempts;
    }

    @Override

    public void requestOtp(ForgotPasswordRequest request) {
        requestOtpForEmail(normalize(request.email()));
    }

    private void requestOtpForEmail(String email) {
        Integer userId = users.findIdByEmail(email).orElse(null);
        if (userId == null) {
            return;
        }
        User user = users.findByIdForUpdate(userId).orElse(null);
        if (!eligible(user)) {
            return;
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        PasswordOtp previous = otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(
                userId, PasswordOtpPurpose.PASSWORD_RESET).orElse(null);
        if (previous != null && previous.getCreatedAt().plus(cooldown).isAfter(now)) {
            return;
        }
        if (previous != null && previous.getUsedAt() == null) {
            previous.setUsedAt(now);
            otps.saveAndFlush(previous);
        }

        String otp = generator.generate();
        PasswordOtp entry = otps.createAndFlush(new PasswordOtpStore.NewPasswordOtp(
                user, encoder.encode(otp), PasswordOtpPurpose.PASSWORD_RESET, now.plus(ttl), now));
        try {
            events.publishEvent(new PasswordResetOtpRequested(entry.getId(), user.getEmail(), otp,
                    entry.getOtpHash(), entry.getExpiresAt().toInstant(ZoneOffset.UTC)));
        } catch (RuntimeException exception) {
            // Keep the public response independent of whether the email exists.
            entry.setOtpDeliveryStatus(OtpDeliveryStatus.FAILED);
            otps.saveAndFlush(entry);
        }
    }

    @Override

    public PasswordChangeResponse reset(ResetPasswordRequest request) {
        ResetResult result;
        try {
            result = resetForEmail(normalize(request.email()), request);
        } catch (PersistenceConflictException exception) {
            throw new UseCaseException(StatusCode.BAD_REQUEST, "Invalid or expired OTP");
        }
        if (result.errorStatus() != null) {
            throw new UseCaseException(result.errorStatus(), result.errorMessage());
        }
        return result.response();
    }

    private ResetResult resetForEmail(String email, ResetPasswordRequest request) {
        Integer userId = users.findIdByEmail(email).orElse(null);
        if (userId == null) {
            return invalidOtp();
        }
        User user = users.findByIdForUpdate(userId).orElse(null);
        if (!eligible(user)) {
            return invalidOtp();
        }
        PasswordOtp entry = otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(
                userId, PasswordOtpPurpose.PASSWORD_RESET).orElse(null);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (entry == null || entry.getUsedAt() != null || !entry.getExpiresAt().isAfter(now)) {
            return invalidOtp();
        }
        if (entry.getFailedAttempts() >= maxAttempts) {
            return ResetResult.failure(StatusCode.TOO_MANY_REQUESTS, "Too many invalid OTP attempts");
        }
        if (!encoder.matches(request.otp(), entry.getOtpHash())) {
            int attempts = entry.getFailedAttempts() + 1;
            entry.setFailedAttempts(attempts);
            otps.saveAndFlush(entry);
            return attempts >= maxAttempts
                    ? ResetResult.failure(StatusCode.TOO_MANY_REQUESTS, "Too many invalid OTP attempts")
                    : invalidOtp();
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResetResult.failure(StatusCode.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            return ResetResult.failure(StatusCode.BAD_REQUEST, "New password must differ from current password");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setUpdatedAt(now);
        entry.setUsedAt(now);
        otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(userId, PasswordOtpPurpose.PASSWORD_CHANGE)
                .filter(changeOtp -> changeOtp.getUsedAt() == null)
                .ifPresent(changeOtp -> {
                    changeOtp.setUsedAt(now);
                    otps.saveAndFlush(changeOtp);
                });
        users.saveAndFlush(user);
        otps.saveAndFlush(entry);
        refreshTokens.revokeActiveByUserId(userId, now);
        return ResetResult.success(new PasswordChangeResponse(now.toInstant(ZoneOffset.UTC)));
    }

    private boolean eligible(User user) {
        return user != null && user.isActiveAndVerified();
    }

    private String normalize(String email) {
        return Email.of(email).value();
    }

    private ResetResult invalidOtp() {
        return ResetResult.failure(StatusCode.BAD_REQUEST, "Invalid or expired OTP");
    }

    public record ResetResult(PasswordChangeResponse response, StatusCode errorStatus, String errorMessage) {
        public static ResetResult success(PasswordChangeResponse response) {
            return new ResetResult(response, null, null);
        }

        public static ResetResult failure(StatusCode status, String message) {
            return new ResetResult(null, status, message);
        }
    }
}
