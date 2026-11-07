package fu.mss301.booking.application.exception;

public class RefundNotFoundException extends RuntimeException {
    public RefundNotFoundException() {
        super("Refund not found");
    }
}
