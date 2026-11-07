package fu.mss301.booking.application.exception;

public class ComboUnavailableException extends RuntimeException {
    public ComboUnavailableException(Integer comboId) {
        super("Combo " + comboId + " is not on sale");
    }
}
