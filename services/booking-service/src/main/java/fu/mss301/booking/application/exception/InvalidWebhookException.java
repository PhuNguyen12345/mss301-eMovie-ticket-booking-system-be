package fu.mss301.booking.application.exception;

public class InvalidWebhookException extends RuntimeException {
    public InvalidWebhookException(String message) {
        super(message);
    }
}
