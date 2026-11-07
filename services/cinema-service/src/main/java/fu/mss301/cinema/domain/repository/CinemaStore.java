package fu.mss301.cinema.domain.repository;

import fu.mss301.cinema.domain.model.*;
import java.util.List;
import java.util.Optional;

public interface CinemaStore {
    Optional<Cinema> cinema(int id);
    List<Cinema> cinemas();
    Cinema save(Cinema cinema);
    Optional<Screen> screen(int id);
    List<Screen> screens(int cinemaId);
    Screen save(Screen screen);
    Optional<SeatType> seatType(int id);
    List<SeatType> seatTypes();
    SeatType save(SeatType type);
    Optional<Seat> seat(int id);
    List<Seat> seats(int screenId);
    Seat save(Seat seat);
}
