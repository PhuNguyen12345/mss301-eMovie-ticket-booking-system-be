package fu.mss301.movie.exception;

import fu.mss301.common.dto.ApiResponse;
import fu.mss301.movie.controller.MovieRelationController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = MovieRelationController.class)
public class MovieRelationExceptionHandler {
    @ExceptionHandler({MovieNotFoundException.class, GenreNotFoundException.class, PersonNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException exception) {
        return ResponseEntity.status(404).body(ApiResponse.<Void>error(404, exception.getMessage()));
    }

    @ExceptionHandler(MovieRelationConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(MovieRelationConflictException exception) {
        return ResponseEntity.status(409).body(ApiResponse.<Void>error(409, exception.getMessage()));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleDatabaseConflict(RuntimeException exception) {
        return ResponseEntity.status(409).body(
            ApiResponse.<Void>error(409, "Movie relations could not be updated; reload and retry")
        );
    }
}
