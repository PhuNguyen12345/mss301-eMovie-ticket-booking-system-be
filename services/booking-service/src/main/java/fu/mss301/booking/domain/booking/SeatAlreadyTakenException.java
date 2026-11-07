package fu.mss301.booking.domain.booking;

/** Saving a booking lost the race for a seat: another booking holds or owns it. */
public class SeatAlreadyTakenException extends RuntimeException {
    public SeatAlreadyTakenException() {
        super("One or more selected seats are already taken");
    }
}
