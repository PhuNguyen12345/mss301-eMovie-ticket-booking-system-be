package fu.mss301.booking.domain.shared;

import java.util.Map;

/** A violated domain invariant (bad input to an aggregate), independent of transport and persistence. */
public class DomainException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> details;

    public DomainException(String message) {
        this(null, message, Map.of());
    }

    /**
     * @param code    why the request was refused, for clients to show their own text; null for a generic reason
     * @param details values the client text needs, such as the check-in opening hours
     */
    public DomainException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public DomainException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    /** Null when the reason is generic. */
    public ErrorCode code() { return code; }

    public Map<String, Object> details() { return details; }
}
