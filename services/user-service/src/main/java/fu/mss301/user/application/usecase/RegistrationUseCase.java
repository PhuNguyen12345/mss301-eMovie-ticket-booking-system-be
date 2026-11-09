package fu.mss301.user.application.usecase;

import fu.mss301.user.domain.valueobject.PasswordPolicy;
import fu.mss301.user.application.port.in.*;

import fu.mss301.user.application.usecase.*;
import fu.mss301.user.application.usecase.dto.RegisterRequest;
import fu.mss301.user.application.usecase.dto.ResendRegistrationOtpRequest;
import fu.mss301.user.application.usecase.dto.RegistrationOtpResponse;
import fu.mss301.user.domain.model.OtpDeliveryStatus;
import fu.mss301.user.domain.model.Registration;
import fu.mss301.user.domain.valueobject.Email;
import fu.mss301.user.application.port.out.OtpCodeGenerator;
import fu.mss301.user.domain.event.RegistrationOtpRequested;
import fu.mss301.user.domain.repository.RegistrationStore;
import fu.mss301.user.domain.repository.UserStore;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import fu.mss301.user.application.port.out.EventPublisher;
import fu.mss301.user.domain.repository.PersistenceConflictException;
import fu.mss301.user.application.port.out.PasswordHasher;

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
                               Duration ttl,
                               Duration cooldown,
                               int maxResends) {
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

    public RegistrationOtpResponse register(RegisterRequest request) {
        if (PasswordPolicy.tooLong(request.password())) {
            throw new UseCaseException(StatusCode.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
        String email = Email.of(request.email()).value();
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        if (userRepository.existsByEmail(email)) {
            throw new UseCaseException(StatusCode.CONFLICT, "Email is already registered");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new UseCaseException(StatusCode.CONFLICT, "Phone is already registered");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        pendingRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.blocksNewRegistration(now)) {
                throw new UseCaseException(StatusCode.CONFLICT, "Registration already exists");
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
        } catch (PersistenceConflictException ex) {
            throw new UseCaseException(StatusCode.CONFLICT, "Registration already exists");
        }
        events.publishEvent(new RegistrationOtpRequested(pending.getId(), email, otp,
                pending.getOtpHash(), pending.getExpiresAt().toInstant(ZoneOffset.UTC)));
        return new RegistrationOtpResponse(pending.getId(), pending.getExpiresAt().toInstant(ZoneOffset.UTC),
                pending.getNextOtpSendAt().toInstant(ZoneOffset.UTC), OtpDeliveryStatus.PENDING);
    }

    public RegistrationOtpResponse resendOtp(ResendRegistrationOtpRequest request) {
        Registration pending = pendingRepository.findByIdForUpdate(request.registrationId().toString())
                .orElseThrow(() -> new UseCaseException(StatusCode.NOT_FOUND, "Registration not found"));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        switch (pending.resendState(now, maxResends)) {
            case USED -> throw new UseCaseException(StatusCode.CONFLICT, "Registration already confirmed");
            case RESEND_LIMIT_REACHED -> throw new UseCaseException(StatusCode.TOO_MANY_REQUESTS, "OTP resend limit reached");
            case COOLDOWN -> throw new UseCaseException(StatusCode.TOO_MANY_REQUESTS,
                    "Please wait before requesting another OTP", pending.retryAfter(now));
            default -> { }
        }
        if (userRepository.existsByEmail(pending.getEmail())
                || (pending.getPhone() != null && userRepository.existsByPhone(pending.getPhone()))) {
            throw new UseCaseException(StatusCode.CONFLICT, "Email or phone is already registered");
        }
        String otp = otpGenerator.generate();
        pending.resend(passwordEncoder.encode(otp), now, ttl, cooldown, maxResends);
        pendingRepository.saveAndFlush(pending);
        try {
            events.publishEvent(new RegistrationOtpRequested(pending.getId(), pending.getEmail(), otp,
                    pending.getOtpHash(), pending.getExpiresAt().toInstant(ZoneOffset.UTC)));
        } catch (RuntimeException exception) {
            throw new UseCaseException(StatusCode.SERVICE_UNAVAILABLE, "Unable to queue OTP email");
        }
        return new RegistrationOtpResponse(pending.getId(), pending.getExpiresAt().toInstant(ZoneOffset.UTC),
                pending.getNextOtpSendAt().toInstant(ZoneOffset.UTC), OtpDeliveryStatus.PENDING);
    }
}
