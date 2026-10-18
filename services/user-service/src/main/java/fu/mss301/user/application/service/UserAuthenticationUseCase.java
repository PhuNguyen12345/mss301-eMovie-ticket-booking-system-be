package fu.mss301.user.application.service;

import fu.mss301.user.application.port.in.*;
import fu.mss301.common.exception.ApiException;
import fu.mss301.user.application.dto.LoginRequest;
import fu.mss301.user.application.dto.LoginResponse.LoginUserResponse;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.application.port.out.UserStore;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import fu.mss301.user.application.port.out.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAuthenticationUseCase implements UserAuthenticationService {

    private final UserStore userRepository;
    private final PasswordHasher passwordEncoder;
    private final String dummyPasswordHash;

    public UserAuthenticationUseCase(UserStore userRepository, PasswordHasher passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginUserResponse authenticate(LoginRequest request) {
        String password = request.password();
        if (password == null || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);
        // Perform BCrypt even for an unknown email to reduce the timing difference.
        boolean passwordMatches = passwordEncoder.matches(password,
                user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !passwordMatches) {
            throw invalidCredentials();
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is not active");
        }
        if (user.getEmailVerifiedAt() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Email is not verified");
        }

        return new LoginUserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRoles().stream().map(UserRole::name).collect(Collectors.toUnmodifiableSet()));
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
