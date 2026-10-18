package fu.mss301.user.domain.exception;

/** A violated domain invariant, independent of transport and persistence concerns. */
public class DomainException extends IllegalArgumentException {
    public DomainException(String message) {
        super(message);
    }
}
