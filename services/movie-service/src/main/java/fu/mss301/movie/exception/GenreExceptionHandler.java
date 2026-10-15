package fu.mss301.movie.exception;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.movie.controller.GenreController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = GenreController.class)
public class GenreExceptionHandler {
    @ExceptionHandler(GenreNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(GenreNotFoundException exception) {
        return ResponseEntity.status(404).body(ApiResponse.<Void>error(404, exception.getMessage()));
    }

    @ExceptionHandler(GenreConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(GenreConflictException exception) {
        return ResponseEntity.status(409).body(ApiResponse.<Void>error(409, exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(
            ApiResponse.<Void>error(409, "Genre data conflicts with a database constraint")
        );
    }
}
