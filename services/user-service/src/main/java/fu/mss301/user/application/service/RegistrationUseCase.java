package fu.mss301.user.application.service;

import fu.mss301.user.application.port.in.*;
import fu.mss301.common.exception.ApiException;
import fu.mss301.user.application.dto.RegisterRequest;
import fu.mss301.user.application.dto.ResendRegistrationOtpRequest;
import fu.mss301.user.application.dto.RegistrationOtpResponse;
import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.domain.valueobject.Email;
import fu.mss301.user.application.port.out.OtpCodeGenerator;
import fu.mss301.user.application.event.RegistrationOtpRequested;
import fu.mss301.user.application.port.out.RegistrationStore;
import fu.mss301.user.application.port.out.UserStore;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import fu.mss301.user.application.port.out.EventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import fu.mss301.user.application.port.out.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationUseCase implements RegistrationService {
    private final UserStore userRepository;
    private final RegistrationStore pendingRepository;
    private final PasswordHasher passwordEncoder;
    private final OtpCodeGenerator otpGenerator;
    private final EventPublisher events;
    private final Clock clock;
    private final Duration ttl;
    private final Duration cooldown;
    private final int maxResends;

    public RegistrationUseCase(UserStore userRepository, RegistrationStore pendingRepository,
                               PasswordHasher passwordEncoder, OtpCodeGenerator otpGenerator,
                               EventPublisher events, Clock clock,
                               @Value("${registration.otp.ttl:PT5M}") Duration ttl,
                               @Value("${registration.otp.resend-cooldown:PT60S}") Duration cooldown,
                               @Value("${registration.otp.max-resends:5}") int maxResends) {
        if (ttl.isNegative() || ttl.isZero() || cooldown.isNegative() || cooldown.isZero()
                || maxResends < 1) {
            throw new IllegalArgumentException("OTP durations and resend limit must be positive");
        }
        this.userRepository = userRepository;
        this.pendingRepository = pendingRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpGenerator = otpGenerator;
        this.events = events;
        this.clock = clock;
        this.ttl = ttl;
        this.cooldown = cooldown;
        this.maxResends = maxResends;
    }

    @Transactional
    public RegistrationOtpResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        String email = Email.of(request.email()).value();
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "Phone is already registered");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        pendingRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.getConfirmedAt() != null || existing.getExpiresAt().isAfter(now)) {
                throw new ApiException(HttpStatus.CONFLICT, "Registration already exists");
            }
            pendingRepository.delete(existing);
            pendingRepository.flush();
        });

        String otp = otpGenerator.generate();
        RegistrationStore.NewRegistration draft = new RegistrationStore.NewRegistration(
                request.fullName().trim(), email, phone, passwordEncoder.encode(request.password()),
                passwordEncoder.encode(otp), now.plus(ttl), now.plus(cooldown), now);
        Registration pending;
        try {
            pending = pendingRepository.createAndFlush(draft);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "Registration already exists");
        }
        events.publishEvent(new RegistrationOtpRequested(pending.getId(), email, otp,
                pending.getOtpHash(), pending.getExpiresAt().toInstant(ZoneOffset.UTC)));
        return new RegistrationOtpResponse(pending.getId(), pending.getExpiresAt().toInstant(ZoneOffset.UTC),
                pending.getNextOtpSendAt().toInstant(ZoneOffset.UTC), OtpDeliveryStatus.PENDING);
    }

    @Transactional
    public RegistrationOtpResponse resendOtp(ResendRegistrationOtpRequest request) {
        Registration pending = pendingRepository.findByIdForUpdate(request.registrationId().toString())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Registration not found"));
        if (pending.getConfirmedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Registration already confirmed");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (pending.getResendCount() >= maxResends) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP resend limit reached");
        }
        if (pending.getNextOtpSendAt().isAfter(now)) {
            Duration remaining = Duration.between(now, pending.getNextOtpSendAt());
            long retryAfterSeconds = Math.max(1, remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0));
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another OTP", headers);
        }
        if (userRepository.existsByEmail(pending.getEmail())
                || (pending.getPhone() != null && userRepository.existsByPhone(pending.getPhone()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Email or phone is already registered");
        }
        String otp = otpGenerator.generate();
        pending.setOtpHash(passwordEncoder.encode(otp));
        pending.setExpiresAt(now.plus(ttl));
        pending.setNextOtpSendAt(now.plus(cooldown));
        pending.setFailedAttempts(0);
        pending.setResendCount(pending.getResendCount() + 1);
        pending.setOtpDeliveryStatus(OtpDeliveryStatus.PENDING);
        pending.setUpdatedAt(now);
        pendingRepository.saveAndFlush(pending);
        try {
            events.publishEvent(new RegistrationOtpRequested(pending.getId(), pending.getEmail(), otp,
                    pending.getOtpHash(), pending.getExpiresAt().toInstant(ZoneOffset.UTC)));
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to queue OTP email");
        }
        return new RegistrationOtpResponse(pending.getId(), pending.getExpiresAt().toInstant(ZoneOffset.UTC),
                pending.getNextOtpSendAt().toInstant(ZoneOffset.UTC), OtpDeliveryStatus.PENDING);
    }
}
