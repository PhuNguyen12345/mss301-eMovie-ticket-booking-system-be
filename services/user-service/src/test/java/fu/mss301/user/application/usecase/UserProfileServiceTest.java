package fu.mss301.user.application.usecase;

import fu.mss301.user.application.port.in.UserProfileService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.mss301.user.application.port.out.UserProfileReadPort;
import fu.mss301.user.application.projection.UserProfileView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
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

    @Test
    void summariesKeepTheRequestedOrderSkipUnknownIdsAndReadOnce() {
        when(profiles.findAllById(any())).thenReturn(List.of(
                view(74, "Trần Thị Bình", "binh@example.com"), view(73, "Nguyễn Văn An", "an@example.com")));

        var result = service.summaries(List.of(73, 99, 74, 73));

        assertEquals(List.of(73, 74), result.stream().map(r -> r.userId()).toList());
        assertEquals("Nguyễn Văn An", result.getFirst().fullName());
        assertEquals("an@example.com", result.getFirst().email());
        assertEquals("0900000073", result.getFirst().phone());
        assertEquals("ACTIVE", result.getFirst().status());
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(profiles, times(1)).findAllById(captor.capture());
        assertEquals(List.of(73, 99, 74), List.copyOf(captor.getValue()), "Duplicates are read once");
    }

    @Test
    void summariesRejectMissingNonPositiveAndTooManyIds() {
        for (List<Integer> ids : List.of(List.<Integer>of(), List.of(0), List.of(5, -1),
                IntStream.rangeClosed(1, 101).boxed().toList())) {
            UseCaseException error = assertThrows(UseCaseException.class, () -> service.summaries(ids));
            assertEquals(StatusCode.BAD_REQUEST, error.getStatus());
        }
        assertThrows(UseCaseException.class, () -> service.summaries(null));
        assertDoesNotThrow(() -> service.summaries(IntStream.rangeClosed(1, 100).boxed().toList()),
                "Exactly 100 ids is allowed");
        verify(profiles, times(1)).findAllById(any());
    }

    private static UserProfileView view(int id, String name, String email) {
        return new UserProfileView(id, name, email, "09000000" + id, LocalDate.of(1998, 3, 14), "Nam", "Hà Nội",
                null, "secret address", null, null, "ACTIVE", LocalDateTime.of(2026, 10, 1, 0, 0));
    }
}
