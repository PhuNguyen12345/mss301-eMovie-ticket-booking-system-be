package fu.mss301.cinema.application.usecase;

import fu.mss301.cinema.application.port.in.*;
import fu.mss301.cinema.domain.exception.CinemaDomainException;
import fu.mss301.cinema.domain.model.*;
import fu.mss301.cinema.domain.repository.CinemaStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CinemaUseCase implements CinemaCatalog, CinemaManagement {
    private final CinemaStore store;
    public CinemaUseCase(CinemaStore store) { this.store = store; }

    public Cinema getCinema(int id) { positive(id); return store.cinema(id).orElseThrow(() -> new CinemaNotFoundException("Cinema", id)); }
    public List<Cinema> listCinemas() { return store.cinemas(); }
    public Screen getScreen(int id) { positive(id); return store.screen(id).orElseThrow(() -> new CinemaNotFoundException("Screen", id)); }
    public List<Screen> listScreens(int cinemaId) { getCinema(cinemaId); return store.screens(cinemaId); }
    public SeatType getSeatType(int id) { positive(id); return store.seatType(id).orElseThrow(() -> new CinemaNotFoundException("Seat type", id)); }
    public List<SeatType> listSeatTypes() { return store.seatTypes(); }
    public Seat getSeat(int id) { positive(id); return store.seat(id).orElseThrow(() -> new CinemaNotFoundException("Seat", id)); }
    public List<Seat> listSeats(int screenId) {
        Screen screen = getScreen(screenId);
        Cinema cinema = getCinema(screen.cinemaId());
        List<Seat> seats = store.seats(screenId);
        if (screen.status() == FacilityStatus.ACTIVE && cinema.status() == FacilityStatus.ACTIVE) return seats;
        return seats.stream().map(seat -> seat.withStatus(FacilityStatus.INACTIVE)).toList();
    }

    @Transactional
    public Cinema createCinema(String code, String name, String location) {
        return store.save(new Cinema(null, code, name, location, FacilityStatus.ACTIVE));
    }
    @Transactional
    public Cinema updateCinema(int id, String code, String name, String location) {
        Cinema old = getCinema(id);
        return store.save(new Cinema(id, code, name, location, old.status()));
    }
    @Transactional
    public Cinema setCinemaStatus(int id, FacilityStatus status) { return store.save(getCinema(id).withStatus(status)); }
    @Transactional
    public Screen createScreen(int cinemaId, String code, String name) {
        getCinema(cinemaId);
        return store.save(new Screen(null, cinemaId, code, name, FacilityStatus.ACTIVE));
    }
    @Transactional
    public Screen updateScreen(int id, String code, String name) {
        Screen old = getScreen(id);
        return store.save(new Screen(id, old.cinemaId(), code, name, old.status()));
    }
    @Transactional
    public Screen setScreenStatus(int id, FacilityStatus status) { return store.save(getScreen(id).withStatus(status)); }
    @Transactional
    public SeatType createSeatType(String code, String name) { return store.save(new SeatType(null, code, name)); }
    @Transactional
    public SeatType updateSeatType(int id, String code, String name) { getSeatType(id); return store.save(new SeatType(id, code, name)); }
    @Transactional
    public Seat createSeat(int screenId, String rowCode, int seatNumber, int seatTypeId) {
        getScreen(screenId);
        getSeatType(seatTypeId);
        return store.save(new Seat(null, screenId, rowCode, seatNumber, seatTypeId, FacilityStatus.ACTIVE));
    }
    @Transactional
    public Seat setSeatStatus(int id, FacilityStatus status) { return store.save(getSeat(id).withStatus(status)); }

    private void positive(int id) { if (id <= 0) throw new CinemaDomainException("ID must be positive"); }
}
