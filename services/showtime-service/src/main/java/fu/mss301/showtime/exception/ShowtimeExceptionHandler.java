package fu.mss301.showtime.exception;

import fu.mss301.common.dto.ApiResponse;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ShowtimeExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ShowtimeExceptionHandler.class);

    @ExceptionHandler(ShowtimeNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound(ShowtimeNotFoundException exception) {
        return error(404, exception.getMessage());
    }

    @ExceptionHandler(ShowtimeConflictException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(ShowtimeConflictException exception) {
        return error(409, exception.getMessage());
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Void>> concurrentWrite(ConcurrencyFailureException exception) {
        return error(409, "Schedule changed concurrently; retry the request");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException exception) {
        return error(400, exception.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalidInput(Exception exception) {
        return error(400, "Invalid request; check JSON, IDs, dates and status values");
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiResponse<Void>> upstream(UpstreamServiceException exception) {
        return error(502, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        if (exception instanceof ErrorResponse response) {
            return error(response.getStatusCode().value(), response.getBody().getTitle());
        }
        log.error("Showtime request failed", exception);
        return error(500, "Internal Server Error");
    }

    private ResponseEntity<ApiResponse<Void>> error(int status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status, message));
    }
}
