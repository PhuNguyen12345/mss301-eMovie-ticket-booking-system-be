package fu.mss301.booking.application.exception;

public class ComboNotFoundException extends RuntimeException {
    public ComboNotFoundException() {
        super("Combo not found");
    }
}
