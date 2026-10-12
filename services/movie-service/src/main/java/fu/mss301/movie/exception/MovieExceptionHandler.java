package fu.mss301.movie.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import fu.mss301.common.dto.*;
@RestControllerAdvice 
public class MovieExceptionHandler {
    
    @ExceptionHandler (MovieNotFoundException.class) 
    public ResponseEntity<ApiResponse<Void>> handleNotFound(
        MovieNotFoundException exception
    ) {
        return ResponseEntity.status(404).body(
            ApiResponse.<Void>error(404, exception.getMessage())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
        IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest().body(
            ApiResponse.<Void>error(400, exception.getMessage())
        );
    }
    
}
