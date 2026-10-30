package fu.mss301.booking.application.port;

import java.util.List;

/** Physical seat layout of a screen, translated from cinema-service. */
public interface SeatLayoutProvider {
    List<PhysicalSeat> getSeats(Integer screenId);

    record PhysicalSeat(Integer id, String rowCode, Integer seatNumber, Integer seatTypeId, boolean usable) {}
}
