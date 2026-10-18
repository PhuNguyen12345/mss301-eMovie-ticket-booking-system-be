package fu.mss301.user.application.service;

import fu.mss301.user.application.port.in.UserProfileService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.mss301.common.exception.ApiException;
import fu.mss301.user.domain.model.User;
import fu.mss301.user.application.port.out.UserStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UserProfileServiceTest {
    private final UserStore users = mock(UserStore.class);
    private final UserProfileService service = new UserProfileUseCase(users);

    @Test
    void returnsOnlyProfileFieldsForAuthenticatedUser() {
        when(users.findById(42)).thenReturn(Optional.of(User.builder()
                .id(42).fullName("Customer").email("customer@example.com").phone("0912345678")
                .passwordHash("secret-hash").status("ACTIVE")
                .emailVerifiedAt(LocalDateTime.of(2026, 10, 1, 0, 0))
                .dateOfBirth(LocalDate.of(2000, 1, 2)).cityProvince("Ha Noi")
                .address("123 Main Street").build()));

        var profile = service.me(42);

        assertEquals(42, profile.userId());
        assertEquals("Customer", profile.fullName());
        assertEquals("0912345678", profile.phone());
        assertEquals(LocalDate.of(2000, 1, 2), profile.dateOfBirth());
        assertEquals("Ha Noi", profile.cityProvince());
        assertFalse(profile.toString().contains("secret-hash"));
        verify(users).findById(42);
    }

    @Test
    void rejectsInactiveUser() {
        when(users.findById(42)).thenReturn(Optional.of(User.builder()
                .id(42).status("SUSPENDED").emailVerifiedAt(LocalDateTime.now()).build()));
        ApiException error = assertThrows(ApiException.class, () -> service.me(42));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void missingUserReturns404() {
        ApiException error = assertThrows(ApiException.class, () -> service.me(42));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }
}
