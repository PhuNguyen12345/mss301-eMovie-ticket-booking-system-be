package fu.mss301.booking.application.exception;

import fu.mss301.booking.application.booking.CheckInView;

/** A scanned code whose seats are all checked in already; carries the earlier scans for the door staff. */
public class TicketAlreadyUsedException extends RuntimeException {
    private final transient CheckInView booking;

    public TicketAlreadyUsedException(CheckInView booking) {
        super("Every seat of this booking has already been checked in");
        this.booking = booking;
    }

    public CheckInView booking() {
        return booking;
    }
}
