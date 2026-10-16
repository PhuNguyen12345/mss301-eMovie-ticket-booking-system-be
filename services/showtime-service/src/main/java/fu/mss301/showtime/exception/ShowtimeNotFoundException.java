package fu.mss301.showtime.exception;

public class ShowtimeNotFoundException extends RuntimeException {
    public ShowtimeNotFoundException(Long id) {
        super("Showtime not found: " + id);
    }
}
