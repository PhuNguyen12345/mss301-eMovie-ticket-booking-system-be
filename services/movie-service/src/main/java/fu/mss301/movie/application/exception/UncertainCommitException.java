package fu.mss301.movie.application.exception;
public class UncertainCommitException extends RuntimeException {
    public UncertainCommitException(Throwable cause) { super("Transaction outcome requires reconciliation", cause); }
}
