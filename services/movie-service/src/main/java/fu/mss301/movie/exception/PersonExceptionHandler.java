package fu.mss301.movie.exception;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.movie.controller.PersonController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PersonController.class)
public class PersonExceptionHandler {
    @ExceptionHandler(PersonNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(PersonNotFoundException exception) {
        return ResponseEntity.status(404).body(ApiResponse.<Void>error(404, exception.getMessage()));
    }

    @ExceptionHandler(PersonConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(PersonConflictException exception) {
        return ResponseEntity.status(409).body(ApiResponse.<Void>error(409, exception.getMessage()));
    }
}
