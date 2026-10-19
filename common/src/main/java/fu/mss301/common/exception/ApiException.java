package fu.mss301.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final HttpHeaders headers;

    public ApiException(HttpStatus status, String message) {
        this(status, message, new HttpHeaders());
    }

    public ApiException(HttpStatus status, String message, HttpHeaders headers) {
        super(message);
        this.status = status;
        this.headers = headers;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }
}
