package fu.mss301.user.domain.repository;

/** Storage conflict that callers may translate into a business response. */
public final class PersistenceConflictException extends RuntimeException {
    public PersistenceConflictException(Throwable cause) {
        super(cause);
    }
}
