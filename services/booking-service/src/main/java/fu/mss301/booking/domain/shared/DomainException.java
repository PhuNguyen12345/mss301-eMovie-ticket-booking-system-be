package fu.mss301.booking.domain.shared;

/** A violated domain invariant (bad input to an aggregate), independent of transport and persistence. */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
