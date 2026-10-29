package fu.mss301.booking.domain.shared;

/** The aggregate is in a state that does not allow the requested business action. */
public class InvalidStateTransitionException extends DomainException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
