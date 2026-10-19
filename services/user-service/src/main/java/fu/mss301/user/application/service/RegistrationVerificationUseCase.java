package fu.mss301.user.application.service;

import fu.mss301.common.exception.ApiException;
import fu.mss301.user.application.port.in.RegistrationVerificationService;
import fu.mss301.user.application.dto.RegisterResponse;
import fu.mss301.user.application.dto.VerifyRegistrationRequest;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.application.port.out.RegistrationStore;
import fu.mss301.user.application.port.out.RoleStore;
import fu.mss301.user.application.port.out.UserStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import fu.mss301.user.application.port.out.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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
    @Transactional(noRollbackFor = ApiException.class)
    public RegisterResponse verify(VerifyRegistrationRequest request) {
        VerificationResult result;
        try {
            result = verifyRegistration(request);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Registration could not be confirmed");
        }
        if (result.errorStatus() != null) {
            throw new ApiException(result.errorStatus(), result.errorMessage());
        }
        return result.user();
    }

    private VerificationResult verifyRegistration(VerifyRegistrationRequest request) {
        var pending = pendingRepository.findByIdForUpdate(request.registrationId().toString()).orElse(null);
        if (pending == null) {
            return VerificationResult.failure(HttpStatus.NOT_FOUND, "Registration not found");
        }
        if (pending.getConfirmedAt() != null) {
            return VerificationResult.failure(HttpStatus.CONFLICT, "Registration already confirmed");
        }
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (!pending.getExpiresAt().isAfter(now)) {
            return VerificationResult.failure(HttpStatus.GONE, "OTP has expired");
        }
        if (pending.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            return VerificationResult.failure(HttpStatus.TOO_MANY_REQUESTS, "Too many invalid OTP attempts");
        }
        if (!passwordEncoder.matches(request.otp(), pending.getOtpHash())) {
            int failedAttempts = pending.getFailedAttempts() + 1;
            pending.setFailedAttempts(failedAttempts);
            pending.setUpdatedAt(now);
            pendingRepository.saveAndFlush(pending);
            return VerificationResult.failure(failedAttempts >= MAX_FAILED_ATTEMPTS
                    ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
                    failedAttempts >= MAX_FAILED_ATTEMPTS
                            ? "Too many invalid OTP attempts" : "Invalid OTP");
        }
        if (userRepository.existsByEmail(pending.getEmail())
                || (pending.getPhone() != null && userRepository.existsByPhone(pending.getPhone()))) {
            return VerificationResult.failure(HttpStatus.CONFLICT, "Email or phone is already registered");
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

    public record VerificationResult(RegisterResponse user, HttpStatus errorStatus, String errorMessage) {
        public static VerificationResult success(RegisterResponse user) {
            return new VerificationResult(user, null, null);
        }

        public static VerificationResult failure(HttpStatus status, String message) {
            return new VerificationResult(null, status, message);
        }
    }
}
