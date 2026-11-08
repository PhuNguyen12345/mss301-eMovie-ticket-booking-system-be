package fu.mss301.booking.domain.booking;

import fu.mss301.booking.domain.shared.InvalidStateTransitionException;

/** Everyone the booking paid for is already in; the ticket cannot be used again. */
public class AlreadyCheckedInException extends InvalidStateTransitionException {
    public AlreadyCheckedInException() {
        super("Every seat of this booking has already been checked in");
    }
}
