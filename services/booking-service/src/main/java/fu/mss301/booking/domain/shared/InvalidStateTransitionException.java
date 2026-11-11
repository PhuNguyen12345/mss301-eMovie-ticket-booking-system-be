package fu.mss301.booking.domain.shared;

import java.util.Map;

/** The aggregate is in a state that does not allow the requested business action. */
public class InvalidStateTransitionException extends DomainException {
    public InvalidStateTransitionException(String message) {
        super(message);
    }

    public InvalidStateTransitionException(ErrorCode code, String message) {
        super(code, message);
    }

    public InvalidStateTransitionException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
