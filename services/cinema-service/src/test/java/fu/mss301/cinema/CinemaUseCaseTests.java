package fu.mss301.cinema;

import fu.mss301.cinema.application.usecase.CinemaNotFoundException;
import fu.mss301.cinema.application.usecase.CinemaUseCase;
import fu.mss301.cinema.domain.exception.CinemaDomainException;
import fu.mss301.cinema.domain.model.*;
import fu.mss301.cinema.domain.repository.CinemaStore;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CinemaUseCaseTests {
    private final CinemaStore store = mock(CinemaStore.class);
    private final CinemaUseCase useCase = new CinemaUseCase(store);

    @Test
    void showtimeCanReadScreenAndCinemaContract() {
        when(store.cinema(1)).thenReturn(Optional.of(new Cinema(1, "C1", "Central", "Ha Noi", FacilityStatus.ACTIVE)));
        when(store.screen(2)).thenReturn(Optional.of(new Screen(2, 1, "S1", "Screen 1", FacilityStatus.ACTIVE)));
        assertEquals(1, useCase.getScreen(2).cinemaId());
        assertEquals("Ha Noi", useCase.getCinema(1).location());
    }

    @Test
    void seatCreationRequiresExistingScreenAndType() {
        when(store.screen(2)).thenReturn(Optional.of(new Screen(2, 1, "S1", "Screen 1", FacilityStatus.ACTIVE)));
        assertThrows(CinemaNotFoundException.class, () -> useCase.createSeat(2, "A", 1, 3));
        verify(store, never()).save(any(Seat.class));
    }

    @Test
    void cinemaCannotUseMaintenanceStatus() {
        when(store.cinema(1)).thenReturn(Optional.of(new Cinema(1, "C1", "Central", "Ha Noi", FacilityStatus.ACTIVE)));
        assertThrows(CinemaDomainException.class, () -> useCase.setCinemaStatus(1, FacilityStatus.MAINTENANCE));
    }

    @Test
    void seatPositionMustBeValid() {
        assertThrows(CinemaDomainException.class, () -> new Seat(null, 2, "a", 1, 3, FacilityStatus.ACTIVE));
        assertThrows(CinemaDomainException.class, () -> new Seat(null, 2, "A", 0, 3, FacilityStatus.ACTIVE));
    }

    @Test
    void closedScreenMakesItsSeatsUnavailableToBooking() {
        when(store.screen(2)).thenReturn(Optional.of(new Screen(2, 1, "S1", "Screen 1", FacilityStatus.MAINTENANCE)));
        when(store.cinema(1)).thenReturn(Optional.of(new Cinema(1, "C1", "Central", "Ha Noi", FacilityStatus.ACTIVE)));
        when(store.seats(2)).thenReturn(List.of(new Seat(3, 2, "A", 1, 4, FacilityStatus.ACTIVE)));
        assertEquals(FacilityStatus.INACTIVE, useCase.listSeats(2).getFirst().status());
    }
}
