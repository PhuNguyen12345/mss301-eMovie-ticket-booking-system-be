package fu.mss301.cinema.application.port.in;

import fu.mss301.cinema.domain.model.*;

public interface CinemaManagement {
    Cinema createCinema(String code, String name, String location);
    Cinema updateCinema(int id, String code, String name, String location);
    Cinema setCinemaStatus(int id, FacilityStatus status);
    Screen createScreen(int cinemaId, String code, String name);
    Screen updateScreen(int id, String code, String name);
    Screen setScreenStatus(int id, FacilityStatus status);
    SeatType createSeatType(String code, String name);
    SeatType updateSeatType(int id, String code, String name);
    Seat createSeat(int screenId, String rowCode, int seatNumber, int seatTypeId);
    Seat setSeatStatus(int id, FacilityStatus status);
}
