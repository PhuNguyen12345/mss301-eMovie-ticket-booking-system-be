package fu.mss301.cinema.application.port.in;

import fu.mss301.cinema.domain.model.*;
import java.util.List;

public interface CinemaCatalog {
    Cinema getCinema(int id);
    List<Cinema> listCinemas();
    Screen getScreen(int id);
    List<Screen> listScreens(int cinemaId);
    SeatType getSeatType(int id);
    List<SeatType> listSeatTypes();
    Seat getSeat(int id);
    List<Seat> listSeats(int screenId);
}
