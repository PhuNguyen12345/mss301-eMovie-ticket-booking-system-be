package fu.mss301.user.application.service;

import fu.mss301.user.application.port.in.PasswordChangeService;
import fu.mss301.common.exception.ApiException;
import fu.mss301.user.application.dto.ConfirmPasswordChangeRequest;
import fu.mss301.user.application.dto.PasswordChangeOtpRequest;
import fu.mss301.user.application.dto.PasswordChangeOtpResponse;
import fu.mss301.user.application.dto.PasswordChangeResponse;
import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.domain.model.PasswordOtp;
import fu.mss301.user.domain.model.PasswordOtpPurpose;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.application.port.out.OtpCodeGenerator;
import fu.mss301.user.application.event.PasswordChangeOtpRequested;
import fu.mss301.user.application.port.out.PasswordOtpStore;
import fu.mss301.user.application.port.out.RefreshTokenStore;
import fu.mss301.user.application.port.out.UserStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import fu.mss301.user.application.port.out.EventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.dao.ConcurrencyFailureException;
import fu.mss301.user.application.port.out.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordChangeUseCase implements PasswordChangeService {
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

    public PasswordChangeUseCase(UserStore users, PasswordOtpStore otps,
                                 RefreshTokenStore refreshTokens, PasswordHasher encoder,
                                 OtpCodeGenerator generator, EventPublisher events, Clock clock,
                                 @Value("${password-change.otp.ttl:PT5M}") Duration ttl,
                                 @Value("${password-change.otp.resend-cooldown:PT60S}") Duration cooldown,
                                 @Value("${password-change.otp.max-attempts:5}") int maxAttempts) {
        if (ttl.isZero() || ttl.isNegative() || cooldown.isZero() || cooldown.isNegative() || maxAttempts < 1) {
            throw new IllegalArgumentException("Password change OTP limits must be positive");
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
    @Transactional
    public PasswordChangeOtpResponse requestOtp(Integer userId, PasswordChangeOtpRequest request) {
        return requestOtpForUser(userId, request.currentPassword());
    }

    private PasswordChangeOtpResponse requestOtpForUser(Integer userId, String currentPassword) {
        User user = activeUserForUpdate(userId);
        if (currentPassword == null || currentPassword.isBlank()
                || currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid current password");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        PasswordOtp previous = otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(
                userId, PasswordOtpPurpose.PASSWORD_CHANGE).orElse(null);
        if (previous != null && previous.getCreatedAt().plus(cooldown).isAfter(now)) {
            Duration remaining = Duration.between(now, previous.getCreatedAt().plus(cooldown));
            long retryAfter = Math.max(1, remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0));
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another OTP", headers);
        }
        if (previous != null && previous.getUsedAt() == null) {
            previous.setUsedAt(now);
            otps.saveAndFlush(previous);
        }

        String otp = generator.generate();
        PasswordOtp entry = otps.createAndFlush(new PasswordOtpStore.NewPasswordOtp(
                user, encoder.encode(otp), PasswordOtpPurpose.PASSWORD_CHANGE, now.plus(ttl), now));
        try {
            events.publishEvent(new PasswordChangeOtpRequested(entry.getId(), user.getEmail(), otp,
                    entry.getOtpHash(), entry.getExpiresAt().toInstant(ZoneOffset.UTC)));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to queue password change OTP email");
        }
        return new PasswordChangeOtpResponse(entry.getExpiresAt().toInstant(ZoneOffset.UTC),
                now.plus(cooldown).toInstant(ZoneOffset.UTC), OtpDeliveryStatus.PENDING);
    }

    @Override
    @Transactional(noRollbackFor = ApiException.class)
    public PasswordChangeResponse confirm(Integer userId, ConfirmPasswordChangeRequest request) {
        ConfirmationResult result;
        try {
            result = confirmForUser(userId, request);
        } catch (ConcurrencyFailureException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Password change could not be completed");
        }
        if (result.errorStatus() != null) {
            throw new ApiException(result.errorStatus(), result.errorMessage());
        }
        return result.response();
    }

    private ConfirmationResult confirmForUser(Integer userId, ConfirmPasswordChangeRequest request) {
        User user = activeUserForUpdate(userId);
        PasswordOtp entry = otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(
                userId, PasswordOtpPurpose.PASSWORD_CHANGE).orElse(null);
        if (entry == null) {
            return ConfirmationResult.failure(HttpStatus.NOT_FOUND, "Password change OTP not requested");
        }
        if (entry.getUsedAt() != null) {
            return ConfirmationResult.failure(HttpStatus.CONFLICT, "Password change OTP already used");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!entry.getExpiresAt().isAfter(now)) {
            return ConfirmationResult.failure(HttpStatus.GONE, "OTP has expired");
        }
        if (entry.getFailedAttempts() >= maxAttempts) {
            return ConfirmationResult.failure(HttpStatus.TOO_MANY_REQUESTS, "Too many invalid OTP attempts");
        }
        if (!encoder.matches(request.otp(), entry.getOtpHash())) {
            int attempts = entry.getFailedAttempts() + 1;
            entry.setFailedAttempts(attempts);
            otps.saveAndFlush(entry);
            return ConfirmationResult.failure(attempts >= maxAttempts
                            ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
                    attempts >= maxAttempts ? "Too many invalid OTP attempts" : "Invalid OTP");
        }
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ConfirmationResult.failure(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        if (encoder.matches(request.newPassword(), user.getPasswordHash())) {
            return ConfirmationResult.failure(HttpStatus.BAD_REQUEST, "New password must differ from current password");
        }
        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setUpdatedAt(now);
        entry.setUsedAt(now);
        otps.findTopByUser_IdAndPurposeOrderByCreatedAtDescIdDesc(userId, PasswordOtpPurpose.PASSWORD_RESET)
                .filter(resetOtp -> resetOtp.getUsedAt() == null)
                .ifPresent(resetOtp -> {
                    resetOtp.setUsedAt(now);
                    otps.saveAndFlush(resetOtp);
                });
        users.saveAndFlush(user);
        otps.saveAndFlush(entry);
        refreshTokens.revokeActiveByUserId(userId, now);
        return ConfirmationResult.success(new PasswordChangeResponse(now.toInstant(ZoneOffset.UTC)));
    }

    private User activeUserForUpdate(Integer userId) {
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "UserAccount not found"));
        if (!user.isActiveAndVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active or email is not verified");
        }
        return user;
    }

    public record ConfirmationResult(PasswordChangeResponse response, HttpStatus errorStatus, String errorMessage) {
        public static ConfirmationResult success(PasswordChangeResponse response) {
            return new ConfirmationResult(response, null, null);
        }

        public static ConfirmationResult failure(HttpStatus status, String message) {
            return new ConfirmationResult(null, status, message);
        }
    }
}
