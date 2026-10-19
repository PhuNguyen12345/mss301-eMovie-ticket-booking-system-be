package fu.mss301.user.application.service;

import fu.mss301.user.application.port.in.UserAuthenticationService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.mss301.common.exception.ApiException;
import fu.mss301.user.application.dto.LoginRequest;
import fu.mss301.user.domain.model.UserRole;
import fu.mss301.user.domain.model.RoleName;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.application.port.out.UserStore;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import fu.mss301.user.infrastructure.security.SpringPasswordHasher;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {
    @Mock
    private UserStore repository;

    private UserAuthenticationService service;
    private User user;

    @BeforeEach
    void setUp() {
        var encoder = new BCryptPasswordEncoder();
        service = new UserAuthenticationUseCase(repository, new SpringPasswordHasher(encoder));
        user = User.builder().id(1).fullName("Administrator").email("admin@gmail.com")
                .passwordHash(encoder.encode("1-8")).status("ACTIVE")
                .emailVerifiedAt(LocalDateTime.of(2026, 10, 12, 0, 0))
                .roles(Set.of(new UserRole(1, RoleName.ADMIN))).build();
    }

    @Test
    void authenticatesActiveVerifiedUserAndReturnsRoles() {
        when(repository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(user));
        var result = service.authenticate(new LoginRequest(" Admin@Gmail.com ", "1-8"));
        assertEquals(1, result.userId());
        assertEquals(Set.of(RoleName.ADMIN), result.roles());
        assertEquals("admin@gmail.com", result.email());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void seededAdminPasswordMatchesDocumentedCredentials() {
        assertTrue(new BCryptPasswordEncoder().matches("1-8",
                "$2a$10$EdbgoCLXqp56DIvh2h5QpORNujXx6TyO7tVCgzOrWlpBV1lUdrK6q"));
    }

    @Test
    void unknownEmailAndWrongPasswordHaveTheSameError() {
        when(repository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(repository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(user));
        var unknown = assertThrows(ApiException.class,
                () -> service.authenticate(new LoginRequest("unknown@example.com", "1-8")));
        var wrong = assertThrows(ApiException.class,
                () -> service.authenticate(new LoginRequest("admin@gmail.com", "wrong-password")));
        assertEquals(HttpStatus.UNAUTHORIZED, unknown.getStatus());
        assertEquals(unknown.getStatus(), wrong.getStatus());
        assertEquals(unknown.getMessage(), wrong.getMessage());
    }

    @Test
    void rejectsInactiveUserAfterMatchingPassword() {
        user.setStatus("DISABLED");
        when(repository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(user));
        var error = assertThrows(ApiException.class,
                () -> service.authenticate(new LoginRequest("admin@gmail.com", "1-8")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals("Account is not active", error.getMessage());
    }

    @Test
    void rejectsUnverifiedEmailAfterMatchingPassword() {
        user.setEmailVerifiedAt(null);
        when(repository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(user));
        var error = assertThrows(ApiException.class,
                () -> service.authenticate(new LoginRequest("admin@gmail.com", "1-8")));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals("Email is not verified", error.getMessage());
    }

    @Test
    void wrongPasswordDoesNotExposeAccountState() {
        user.setStatus("DISABLED");
        user.setEmailVerifiedAt(null);
        when(repository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(user));
        var error = assertThrows(ApiException.class,
                () -> service.authenticate(new LoginRequest("admin@gmail.com", "wrong-password")));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
    }

    @Test
    void rejectsOversizedUtf8PasswordBeforeBcrypt() {
        var error = assertThrows(ApiException.class,
                () -> service.authenticate(new LoginRequest("admin@gmail.com", "é".repeat(40))));
        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        verifyNoInteractions(repository);
    }
}
