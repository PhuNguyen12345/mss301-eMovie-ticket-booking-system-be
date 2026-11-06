package fu.mss301.user.application.usecase;

public final class UseCaseException extends RuntimeException {
    private final StatusCode status;
    private final Long retryAfterSeconds;

    public UseCaseException(StatusCode status, String message) {
        this(status, message, null);
    }

    public UseCaseException(StatusCode status, String message, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public StatusCode getStatus() {
        return status;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
