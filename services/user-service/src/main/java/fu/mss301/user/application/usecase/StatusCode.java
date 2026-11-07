package fu.mss301.user.application.usecase;

public enum StatusCode {
    BAD_REQUEST(400), UNAUTHORIZED(401), FORBIDDEN(403), NOT_FOUND(404),
    CONFLICT(409), GONE(410), TOO_MANY_REQUESTS(429), SERVICE_UNAVAILABLE(503);

    private final int value;

    StatusCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
