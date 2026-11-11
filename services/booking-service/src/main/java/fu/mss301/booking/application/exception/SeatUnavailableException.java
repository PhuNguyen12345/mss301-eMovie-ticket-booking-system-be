package fu.mss301.booking.application.exception;

import fu.mss301.booking.domain.shared.ErrorCode;

/** A chosen seat cannot be held: someone else has it, or it is out of service. */
public class SeatUnavailableException extends RuntimeException {
    private final ErrorCode code;

    public SeatUnavailableException(String message) {
        this(ErrorCode.SEAT_UNAVAILABLE, message);
    }

    public SeatUnavailableException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() { return code; }
}
