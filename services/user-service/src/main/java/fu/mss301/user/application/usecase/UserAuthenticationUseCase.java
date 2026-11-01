package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.*;

import fu.mss301.user.application.usecase.*;
import fu.mss301.user.application.usecase.dto.LoginRequest;
import fu.mss301.user.application.usecase.dto.LoginResponse.LoginUserResponse;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.domain.repository.UserStore;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Collectors;
import fu.mss301.user.application.port.out.PasswordHasher;

public class UserAuthenticationUseCase implements UserAuthenticationService {

    private final UserStore userRepository;
    private final PasswordHasher passwordEncoder;
    private final String dummyPasswordHash;

    public UserAuthenticationUseCase(UserStore userRepository, PasswordHasher passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

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
            throw new UseCaseException(StatusCode.FORBIDDEN, "Account is not active");
        }
        if (user.getEmailVerifiedAt() == null) {
            throw new UseCaseException(StatusCode.FORBIDDEN, "Email is not verified");
        }

        return new LoginUserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRoles().stream().map(UserRole::name).collect(Collectors.toUnmodifiableSet()));
    }

    private UseCaseException invalidCredentials() {
        return new UseCaseException(StatusCode.UNAUTHORIZED, "Invalid email or password");
    }
}
