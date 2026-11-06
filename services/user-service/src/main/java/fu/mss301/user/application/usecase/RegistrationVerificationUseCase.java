package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.*;

import fu.mss301.user.application.port.in.RegistrationVerificationService;
import fu.mss301.user.application.usecase.dto.RegisterResponse;
import fu.mss301.user.application.usecase.dto.VerifyRegistrationRequest;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.domain.repository.RegistrationStore;
import fu.mss301.user.domain.repository.RoleStore;
import fu.mss301.user.domain.repository.UserStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import fu.mss301.user.domain.repository.PersistenceConflictException;
import fu.mss301.user.application.port.out.PasswordHasher;

public class RegistrationVerificationUseCase implements RegistrationVerificationService {
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final RegistrationStore pendingRepository;
    private final UserStore userRepository;
    private final RoleStore roleRepository;
    private final PasswordHasher passwordEncoder;
    private final Clock clock;

    public RegistrationVerificationUseCase(RegistrationStore pendingRepository,
                                           UserStore userRepository, RoleStore roleRepository,
                                           PasswordHasher passwordEncoder, Clock clock) {
        this.pendingRepository = pendingRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override

    public RegisterResponse verify(VerifyRegistrationRequest request) {
        VerificationResult result;
        try {
            result = verifyRegistration(request);
        } catch (PersistenceConflictException exception) {
            throw new UseCaseException(StatusCode.CONFLICT, "Registration could not be confirmed");
        }
        if (result.errorStatus() != null) {
            throw new UseCaseException(result.errorStatus(), result.errorMessage());
        }
        return result.user();
    }

    private VerificationResult verifyRegistration(VerifyRegistrationRequest request) {
        var pending = pendingRepository.findByIdForUpdate(request.registrationId().toString()).orElse(null);
        if (pending == null) {
            return VerificationResult.failure(StatusCode.NOT_FOUND, "Registration not found");
        }
        if (pending.getConfirmedAt() != null) {
            return VerificationResult.failure(StatusCode.CONFLICT, "Registration already confirmed");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!pending.getExpiresAt().isAfter(now)) {
            return VerificationResult.failure(StatusCode.GONE, "OTP has expired");
        }
        if (pending.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            return VerificationResult.failure(StatusCode.TOO_MANY_REQUESTS, "Too many invalid OTP attempts");
        }
        if (!passwordEncoder.matches(request.otp(), pending.getOtpHash())) {
            int failedAttempts = pending.getFailedAttempts() + 1;
            pending.setFailedAttempts(failedAttempts);
            pending.setUpdatedAt(now);
            pendingRepository.saveAndFlush(pending);
            return VerificationResult.failure(failedAttempts >= MAX_FAILED_ATTEMPTS
                    ? StatusCode.TOO_MANY_REQUESTS : StatusCode.BAD_REQUEST,
                    failedAttempts >= MAX_FAILED_ATTEMPTS
                            ? "Too many invalid OTP attempts" : "Invalid OTP");
        }
        if (userRepository.existsByEmail(pending.getEmail())
                || (pending.getPhone() != null && userRepository.existsByPhone(pending.getPhone()))) {
            return VerificationResult.failure(StatusCode.CONFLICT, "Email or phone is already registered");
        }
        var customerRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is missing"));
        User user = userRepository.createAndFlush(new UserStore.NewUser(
                pending.getFullName(), pending.getEmail(), pending.getPhone(), pending.getPasswordHash(),
                "ACTIVE", now, now, Set.of(customerRole)));
        pending.setConfirmedAt(now);
        pending.setUpdatedAt(now);
        pendingRepository.saveAndFlush(pending);
        return VerificationResult.success(new RegisterResponse(user.getId(), user.getFullName(),
                user.getEmail(), user.getPhone(), user.getStatus()));
    }

    public record VerificationResult(RegisterResponse user, StatusCode errorStatus, String errorMessage) {
        public static VerificationResult success(RegisterResponse user) {
            return new VerificationResult(user, null, null);
        }

        public static VerificationResult failure(StatusCode status, String message) {
            return new VerificationResult(null, status, message);
        }
    }
}
