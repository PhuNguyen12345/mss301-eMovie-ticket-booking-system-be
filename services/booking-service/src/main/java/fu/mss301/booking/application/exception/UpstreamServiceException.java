package fu.mss301.booking.application.exception;

/** A dependent service (showtime, cinema) is unavailable or answered with unusable data. */
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message) {
        super(message);
    }
}
