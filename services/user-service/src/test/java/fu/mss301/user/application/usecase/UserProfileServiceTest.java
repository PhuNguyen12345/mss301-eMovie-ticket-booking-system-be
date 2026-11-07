package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.UserProfileService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.mss301.user.application.port.out.UserProfileReadPort;
import fu.mss301.user.application.projection.UserProfileView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserProfileServiceTest {
    private final UserProfileReadPort profiles = mock(UserProfileReadPort.class);
    private final UserProfileService service = new UserProfileUseCase(profiles);

    @Test
    void returnsOnlyProfileFieldsForAuthenticatedUser() {
        when(profiles.findById(42)).thenReturn(Optional.of(new UserProfileView(
                42, "Customer", "customer@example.com", "0912345678",
                LocalDate.of(2000, 1, 2), null, "Ha Noi", null,
                "123 Main Street", null, null, "ACTIVE",
                LocalDateTime.of(2026, 10, 1, 0, 0))));

        var profile = service.me(42);

        assertEquals(42, profile.userId());
        assertEquals("Customer", profile.fullName());
        assertEquals("0912345678", profile.phone());
        assertEquals(LocalDate.of(2000, 1, 2), profile.dateOfBirth());
        assertEquals("Ha Noi", profile.cityProvince());
        verify(profiles).findById(42);
    }

    @Test
    void rejectsInactiveUser() {
        when(profiles.findById(42)).thenReturn(Optional.of(new UserProfileView(
                42, null, null, null, null, null, null, null,
                null, null, null, "SUSPENDED", LocalDateTime.now())));
        UseCaseException error = assertThrows(UseCaseException.class, () -> service.me(42));
        assertEquals(StatusCode.FORBIDDEN, error.getStatus());
    }

    @Test
    void missingUserReturns404() {
        UseCaseException error = assertThrows(UseCaseException.class, () -> service.me(42));
        assertEquals(StatusCode.NOT_FOUND, error.getStatus());
    }
}
